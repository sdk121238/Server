package com.derm.resource;

import com.derm.api.*;
import com.derm.auth.*;
import com.derm.resource.Records.Kind;
import com.derm.storage.UploadService;
import com.derm.sync.SyncService;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ResourceService {
    private final Records records;
    private final JdbcTemplate db;
    private final Json json;
    private final Contract contract;
    private final CaptureValidation captureValidation;
    private final UploadService uploads;
    private final SyncService sync;
    private final PatientDetails patients;
    private final AccessPolicy access;
    public ResourceService(Records records,JdbcTemplate db,Json json,Contract contract,CaptureValidation validation,UploadService uploads,SyncService sync,PatientDetails patients,AccessPolicy access) {
        this.records=records;this.db=db;this.json=json;this.contract=contract;this.captureValidation=validation;this.uploads=uploads;this.sync=sync;
        this.patients=patients;this.access=access;
    }
    private ObjectNode present(Kind kind,ObjectNode data) {return kind==Kind.PATIENT?patients.present(data):data;}
    public ApiResult get(Kind kind,Actor actor,String id) { return ApiResult.resource(200,present(kind,records.read(kind,actor.userId(),id).data())); }
    public ApiResult create(Kind kind,Actor actor,JsonNode input) {
        String user=actor.userId(),id=input.get("id").asText();
        if(records.exists(kind,id)) throw new ApiException(409,"ID_CONFLICT","ID已被使用，请确认原记录");
        String patient=id,lesion=null;
        if(kind==Kind.LESION) { patient=input.get("patientId").asText(); records.get(Kind.PATIENT,user,patient); }
        if(kind==Kind.CAPTURE) { lesion=input.get("lesionId").asText(); patient=records.get(Kind.LESION,user,lesion).patient(); }
        sync.checkImport(user,input,patient);
        ObjectNode data=((ObjectNode)input).deepCopy(); data.remove(List.of("clientCreatedAt","importId"));
        long now=System.currentTimeMillis(); data.put("revision",1).put("createdAt",now).put("updatedAt",now).putNull("deletedAt");
        if(kind==Kind.PATIENT&&!data.has("birthDate")) data.putNull("birthDate");
        if(kind==Kind.PATIENT) patients.normalize(data,input);
        if(kind==Kind.LESION&&!data.has("note")) data.put("note","");
        if(kind==Kind.CAPTURE) {
            for(String field:List.of("analysis","calibration","annotation","qualityNote")) if(!data.has(field)) data.putNull(field);
            if(!data.has("metadata")) data.set("metadata",json.object());
            data.put("contentRevision",1).put("reviewStatus","NOT_SUBMITTED");
            JsonNode file=uploads.readyFile(user,input.get("imageFileId").asText(),patient);
            contract.validate(kind.schema,data); captureValidation.validate(data,file);
        } else contract.validate(kind.schema,data);
        switch(kind) {
            case PATIENT -> db.update("INSERT INTO patients(id,owner_id,revision,payload) VALUES(?,?,1,?)",id,user,json.write(data));
            case LESION -> db.update("INSERT INTO lesions(id,owner_id,patient_id,revision,payload) VALUES(?,?,?,1,?)",id,user,patient,json.write(data));
            case CAPTURE -> {
                db.update("INSERT INTO captures(id,owner_id,patient_id,lesion_id,file_id,captured_at,revision,payload) VALUES(?,?,?,?,?,?,1,?)",id,user,patient,lesion,input.get("imageFileId").asText(),input.get("capturedAt").asLong(),json.write(data));
                records.history(user,data);
            }
        }
        if(kind==Kind.PATIENT) records.patientColumns(data);
        records.event(user,kind,data,"UPSERT"); return ApiResult.resource(201,present(kind,data));
    }
    public ApiResult patch(Kind kind,Actor actor,String id,JsonNode patch,String match) {
        var row=records.get(kind,actor.userId(),id); Preconditions.check(match,row.revision(),id);
        ObjectNode next=json.merge(row.data(),patch);
        if(kind==Kind.PATIENT) patients.normalize(next,patch);
        boolean changed=!next.equals(row.data());
        if(!changed) return ApiResult.resource(200,present(kind,row.data()));
        if(kind==Kind.CAPTURE) {
            boolean measurementChanged=!next.path("annotation").equals(row.data().path("annotation"))||!next.path("calibration").equals(row.data().path("calibration"));
            // A partial analysis patch cannot claim to be a recomputation for changed inputs.
            boolean fullRecalculation=patch.path("analysis").isObject()&&patch.get("analysis").has("isValid")&&patch.get("analysis").has("areaMm2")&&patch.get("analysis").has("algorithmVersion");
            if(measurementChanged&&!fullRecalculation) next.putNull("analysis");
            next.put("contentRevision",row.data().get("contentRevision").asLong()+1).put("reviewStatus","NOT_SUBMITTED");
            contract.validate(kind.schema,next); captureValidation.validate(next,uploads.readyFile(actor.userId(),next.get("imageFileId").asText(),row.patient()));
        } else contract.validate(kind.schema,next);
        next.put("revision",row.revision()+1).put("updatedAt",System.currentTimeMillis());
        records.update(kind,actor.userId(),next); if(kind==Kind.CAPTURE) records.history(actor.userId(),next);
        records.event(actor.userId(),kind,next,"UPSERT"); return ApiResult.resource(200,present(kind,next));
    }
    public ApiResult delete(Kind kind,Actor actor,String id,String match) {
        var row=records.get(kind,actor.userId(),id); Preconditions.check(match,row.revision(),id);
        // Write parent tombstone before its children, all under one scope lock and one transaction.
        tombstone(kind,actor.userId(),row.data());
        if(kind==Kind.PATIENT) {
            for(ObjectNode lesion:records.all(Kind.LESION,actor.userId())) if(lesion.get("patientId").asText().equals(id)) tombstone(Kind.LESION,actor.userId(),lesion);
            for(ObjectNode capture:records.all(Kind.CAPTURE,actor.userId())) if(db.queryForObject("SELECT patient_id FROM captures WHERE id=?",String.class,capture.get("id").asText()).equals(id)) tombstone(Kind.CAPTURE,actor.userId(),capture);
        } else if(kind==Kind.LESION) for(ObjectNode capture:records.all(Kind.CAPTURE,actor.userId())) if(capture.get("lesionId").asText().equals(id)) tombstone(Kind.CAPTURE,actor.userId(),capture);
        return ApiResult.empty();
    }
    private void tombstone(Kind kind,String user,ObjectNode data) {
        data=data.deepCopy(); long now=System.currentTimeMillis();
        data.put("revision",data.get("revision").asLong()+1).put("deletedAt",now).put("updatedAt",now);
        records.update(kind,user,data); if(kind==Kind.CAPTURE) records.history(user,data); records.event(user,kind,data,"DELETE");
    }
    public ApiResult list(Kind kind,Actor actor,String parent,String query,String pageToken,int limit,Long from,Long to) {
        return list(kind,actor,parent,query,pageToken,limit,from,to,"all");
    }
    public ApiResult list(Kind kind,Actor actor,String parent,String query,String pageToken,int limit,Long from,Long to,String scope) {
        if(!Set.of("all","mine").contains(scope)) throw ApiException.invalid("scope只能为all或mine");
        boolean doctor=access.isDoctor(actor.userId());
        if(parent!=null) records.read(kind==Kind.LESION?Kind.PATIENT:Kind.LESION,actor.userId(),parent);
        if(query!=null&&query.length()>128) throw ApiException.invalid("搜索词过长");
        if(from!=null&&to!=null&&from>=to) throw ApiException.invalid("时间区间无效");
        String binding=kind.name()+":"+Objects.toString(parent,"")+":"+Objects.toString(query,"")+":"+from+":"+to+":"+scope+":"+doctor;
        JsonNode position=sync.readPage(pageToken,actor.userId(),binding);
        String lastId=position.path("id").asText(""); long lastTime=position.path("time").asLong(-1);
        StringBuilder where=new StringBuilder(" WHERE r.deleted_at IS NULL");List<Object> args=new ArrayList<>();
        if(!doctor) {where.append(" AND r.owner_id=?");args.add(actor.userId());}
        if(kind==Kind.PATIENT&&doctor&&scope.equals("mine")) {where.append(" AND r.attending_doctor_id=?");args.add(actor.userId());}
        if(kind!=Kind.PATIENT) where.append(" AND EXISTS (SELECT 1 FROM patients p WHERE p.id=r.patient_id AND p.deleted_at IS NULL)");
        if(kind==Kind.CAPTURE) where.append(" AND EXISTS (SELECT 1 FROM lesions l WHERE l.id=r.lesion_id AND l.deleted_at IS NULL)");
        if(parent!=null) {where.append(kind==Kind.LESION?" AND r.patient_id=?":" AND r.lesion_id=?");args.add(parent);}
        if(query!=null) {where.append(" AND LOCATE(?,r.patient_name)>0");args.add(query);}
        if(from!=null) {where.append(" AND r.captured_at>=?");args.add(from);}
        if(to!=null) {where.append(" AND r.captured_at<?");args.add(to);}
        if(!lastId.isEmpty()) {
            if(kind==Kind.CAPTURE) {where.append(" AND (r.captured_at>? OR (r.captured_at=? AND r.id>?))");args.add(lastTime);args.add(lastTime);args.add(lastId);}
            else {where.append(" AND r.id>?");args.add(lastId);}
        }
        args.add(limit+1);
        List<ObjectNode> available=db.query("SELECT r.payload FROM "+kind.table+" r"+where+(kind==Kind.CAPTURE?" ORDER BY r.captured_at,r.id":" ORDER BY r.id")+" LIMIT ?",(rs,n)->(ObjectNode)json.read(rs.getString(1)),args.toArray());
        ArrayNode items=json.array(); for(int i=0;i<Math.min(limit,available.size());i++) items.add(present(kind,available.get(i)));
        ObjectNode out=json.object();out.set("items",items);out.putNull("nextPageToken");
        if(available.size()>limit) {
            JsonNode last=items.get(items.size()-1);out.put("nextPageToken",sync.pageToken(actor.userId(),binding,last.get("id").asText(),kind==Kind.CAPTURE?last.get("capturedAt").asLong():0));
        }
        return ApiResult.ok(out);
    }
    public ApiResult doctors(Actor actor,String query,String token,int limit) {
        access.isDoctor(actor.userId());
        if(query!=null&&query.length()>128) throw ApiException.invalid("搜索词过长");
        String binding="doctors:"+Objects.toString(query,"");String last=sync.readPage(token,actor.userId(),binding).path("id").asText("");
        var rows=db.queryForList("SELECT id,display_name FROM users WHERE role='HOSPITAL_STAFF' AND status='ACTIVE' AND id>? AND LOCATE(?,display_name)>0 ORDER BY id LIMIT ?",last,Objects.toString(query,""),limit+1);
        var items=json.array();for(int i=0;i<Math.min(limit,rows.size());i++) items.add(json.object().put("id",rows.get(i).get("id").toString()).put("displayName",rows.get(i).get("display_name").toString()));
        ObjectNode out=json.object();out.set("items",items);out.putNull("nextPageToken");
        if(rows.size()>limit) out.put("nextPageToken",sync.pageToken(actor.userId(),binding,items.get(items.size()-1).get("id").asText(),0));
        return ApiResult.ok(out);
    }
}
