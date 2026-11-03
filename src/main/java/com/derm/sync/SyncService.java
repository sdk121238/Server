package com.derm.sync;

import com.derm.api.*;
import com.derm.auth.*;
import com.derm.resource.Records;
import com.derm.resource.Records.Kind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class SyncService {
    private final JdbcTemplate db;
    private final Json json;
    private final Crypto crypto;
    private final Records records;
    public SyncService(JdbcTemplate db,Json json,Crypto crypto,Records records) { this.db=db;this.json=json;this.crypto=crypto;this.records=records; }
    private long generation(String owner) { return db.queryForObject("SELECT generation FROM users WHERE id=?",Long.class,owner); }
    private ObjectNode bound(String owner,String kind,long expires) {
        return json.object().put("owner",owner).put("kind",kind).put("generation",generation(owner)).put("expiresAt",expires);
    }
    private JsonNode read(String token,String owner,String kind) {
        JsonNode node=crypto.verify(token,kind,410,"SYNC_RESET_REQUIRED");
        if(!owner.equals(node.path("owner").asText())||generation(owner)!=node.path("generation").asLong()) throw reset(); return node;
    }
    public String pageToken(String owner,String binding,String id,long time) {
        return crypto.sign(bound(owner,"page",System.currentTimeMillis()+86400_000).put("binding",binding).put("id",id).put("time",time));
    }
    public JsonNode readPage(String token,String owner,String binding) {
        if(token==null) return json.object(); JsonNode node=read(token,owner,"page");
        if(!binding.equals(node.path("binding").asText())) throw reset(); return node;
    }
    private String cursor(String owner,long position) { return crypto.sign(bound(owner,"sync",System.currentTimeMillis()+90L*86400_000).put("position",position)); }
    public ApiResult snapshot(Actor actor) {
        String owner=actor.userId(),id=UUID.randomUUID().toString(); long expires=System.currentTimeMillis()+86400_000;
        long position=db.queryForObject("SELECT sync_position FROM users WHERE id=?",Long.class,owner);
        db.update("INSERT INTO sync_snapshots(id,owner_id,generation,position,expires_at) VALUES(?,?,?,?,?)",id,owner,generation(owner),position,expires);
        long ordinal=0;
        for(Kind kind:Kind.values()) for(ObjectNode item:records.all(kind,owner)) {
            ObjectNode change=json.object().put("entityType",kind.name()).put("entityId",item.get("id").asText()).put("operation","UPSERT").put("revision",item.get("revision").asLong()); change.set("payload",item);
            db.update("INSERT INTO sync_snapshot_items(snapshot_id,ordinal,payload) VALUES(?,?,?)",id,++ordinal,json.write(change));
        }
        return new ApiResult(201,json.object().put("id",id).put("expiresAt",expires),null);
    }
    public ApiResult snapshotPage(Actor actor,String id,String token,int limit) {
        var rows=db.queryForList("SELECT * FROM sync_snapshots WHERE id=? AND owner_id=?",id,actor.userId());
        if(rows.isEmpty()) throw ApiException.missing(); var row=rows.getFirst();
        if(((Number)row.get("expires_at")).longValue()<=System.currentTimeMillis()||((Number)row.get("generation")).longValue()!=generation(actor.userId())) throw reset();
        long offset=0;
        if(token!=null) { var payload=read(token,actor.userId(),"snapshot-page"); if(!id.equals(payload.path("snapshotId").asText())) throw reset();offset=payload.get("offset").asLong(); }
        var items=db.queryForList("SELECT ordinal,payload FROM sync_snapshot_items WHERE snapshot_id=? AND ordinal>? ORDER BY ordinal LIMIT ?",id,offset,limit+1);
        ArrayNode values=json.array(); for(int i=0;i<Math.min(limit,items.size());i++) values.add(json.read(items.get(i).get("payload").toString()));
        ObjectNode out=json.object().put("snapshotId",id);out.set("items",values);out.putNull("nextPageToken").putNull("nextCursor");
        if(items.size()>limit) out.put("nextPageToken",crypto.sign(bound(actor.userId(),"snapshot-page",((Number)row.get("expires_at")).longValue()).put("snapshotId",id).put("offset",((Number)items.get(limit-1).get("ordinal")).longValue())));
        else out.put("nextCursor",cursor(actor.userId(),((Number)row.get("position")).longValue()));
        return ApiResult.ok(out);
    }
    public ApiResult changes(Actor actor,String token,int limit) {
        long position=read(token,actor.userId(),"sync").path("position").asLong(-1);
        long upper=db.queryForObject("SELECT sync_position FROM users WHERE id=?",Long.class,actor.userId());
        if(position<0||position>upper) throw reset();
        var rows=db.queryForList("SELECT position,payload FROM sync_changes WHERE owner_id=? AND position>? AND position<=? ORDER BY position LIMIT ?",actor.userId(),position,upper,limit+1);
        ArrayNode items=json.array(); long next=position;
        for(int i=0;i<Math.min(limit,rows.size());i++) { var row=rows.get(i); items.add(json.read(row.get("payload").toString()));next=((Number)row.get("position")).longValue(); }
        ObjectNode out=json.object().put("nextCursor",cursor(actor.userId(),next)).put("hasMore",rows.size()>limit);out.set("items",items);return ApiResult.ok(out);
    }
    public ApiResult createImport(Actor actor,JsonNode input) {
        if(input.hasNonNull("targetOrganizationId")) throw new ApiException(403,"FORBIDDEN","当前未启用机构工作区");
        String id=UUID.randomUUID().toString();long expires=System.currentTimeMillis()+30L*86400_000;
        db.update("INSERT INTO offline_imports(id,owner_id,dataset_id,allowed_patients,expires_at) VALUES(?,?,?,?,?)",id,actor.userId(),input.get("clientDatasetId").asText(),json.write(input.get("confirmedPatientIds")),expires);
        ObjectNode out=json.object().put("id",id).put("expiresAt",expires);out.set("allowedPatientIds",input.get("confirmedPatientIds"));return new ApiResult(201,out,null);
    }
    public void checkImport(String owner,JsonNode input,String patient) {
        if(!input.has("importId")) return;
        var rows=db.queryForList("SELECT allowed_patients,expires_at FROM offline_imports WHERE id=? AND owner_id=?",input.get("importId").asText(),owner);
        if(rows.isEmpty()) throw ApiException.missing();var row=rows.getFirst();
        if(((Number)row.get("expires_at")).longValue()<=System.currentTimeMillis()) throw new ApiException(410,"IMPORT_EXPIRED","导入计划已过期");
        boolean allowed=false;for(JsonNode id:json.read(row.get("allowed_patients").toString())) if(id.asText().equals(patient)) allowed=true;
        if(!allowed) throw new ApiException(403,"FORBIDDEN","患者不在已确认导入范围内");
    }
    private static ApiException reset() { return new ApiException(410,"SYNC_RESET_REQUIRED","同步范围或游标已失效，请重新建立快照"); }
}
