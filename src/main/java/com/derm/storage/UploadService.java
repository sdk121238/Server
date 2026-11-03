package com.derm.storage;

import com.derm.api.*;
import com.derm.auth.*;
import com.derm.resource.Records;
import com.derm.resource.Records.Kind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.io.*;
import java.nio.file.*;

@Service
public class UploadService {
    private final JdbcTemplate db;private final Json json;private final Crypto crypto;private final Records records;
    private final ImageFiles images;private final AuthService auth;private final TransactionTemplate tx;private final String publicUrl;
    public UploadService(JdbcTemplate db,Json json,Crypto crypto,Records records,ImageFiles images,AuthService auth,TransactionTemplate tx,@Value("${app.public-url}")String url) {
        this.db=db;this.json=json;this.crypto=crypto;this.records=records;this.images=images;this.auth=auth;this.tx=tx;
        if(!url.matches("https?://[^?#]+")) throw new IllegalArgumentException("APP_PUBLIC_URL must be an absolute HTTP(S) URL without query or fragment"); publicUrl=url.replaceAll("/+$","");
    }
    public ApiResult create(Actor actor,JsonNode input) {
        String patient=input.get("patientId").asText();records.get(Kind.PATIENT,actor.userId(),patient);
        if(!"SINGLE".equals(input.get("strategy").asText())) throw new ApiException(422,"VALIDATION_ERROR","当前仅支持SINGLE上传");
        if(!Set.of("image/jpeg","image/png").contains(input.get("mimeType").asText())) throw new ApiException(415,"UNSUPPORTED_MEDIA_TYPE","当前支持JPEG和PNG图片");
        if(input.get("width").asLong()>20000||input.get("height").asLong()>20000||input.get("width").asLong()*input.get("height").asLong()>ImageFiles.MAX_PIXELS) throw ApiException.invalid("图片尺寸超过解码上限");
        String id=UUID.randomUUID().toString(),file=UUID.randomUUID().toString();long now=System.currentTimeMillis(),expires=now+86400_000;
        ObjectNode metadata=((ObjectNode)input).deepCopy();metadata.remove(List.of("patientId","strategy"));metadata.put("id",file).put("kind","IMAGE").put("state","PENDING").put("createdAt",now);
        db.update("INSERT INTO files(id,owner_id,patient_id,state,payload) VALUES(?,?,?,'PENDING',?)",file,actor.userId(),patient,json.write(metadata));
        db.update("INSERT INTO file_uploads(id,file_id,owner_id,patient_id,state,expires_at,created_at) VALUES(?,?,?,?,'PENDING',?,?)",id,file,actor.userId(),patient,expires,now);
        return new ApiResult(201,upload(actor.userId(),id),null);
    }
    private Map<String,Object> uploadRow(String owner,String id) {
        var rows=db.queryForList("SELECT * FROM file_uploads WHERE id=? AND owner_id=?",id,owner);
        if(rows.isEmpty()) throw ApiException.missing();var row=rows.getFirst();records.get(Kind.PATIENT,owner,row.get("patient_id").toString());
        if(((Number)row.get("expires_at")).longValue()<=System.currentTimeMillis()) throw new ApiException(410,"UPLOAD_EXPIRED","上传会话已过期");return row;
    }
    public ObjectNode upload(String owner,String id) {
        var row=uploadRow(owner,id);String state=row.get("state").toString();
        ObjectNode out=json.object().put("id",id).put("fileId",row.get("file_id").toString()).put("strategy","SINGLE").put("state",state).put("expiresAt",((Number)row.get("expires_at")).longValue());
        out.set("requiredHeaders",json.object());out.set("acceptedParts",json.array());out.putNull("uploadUrl").putNull("urlExpiresAt");
        if(Set.of("PENDING","UPLOADING").contains(state)) { ObjectNode signed=sign(owner,id);out.put("uploadUrl",signed.get("url").asText()).put("urlExpiresAt",signed.get("urlExpiresAt").asLong());out.set("requiredHeaders",signed.get("requiredHeaders")); }
        return out;
    }
    private String signedUrl(String id,String owner,String kind,long expires) {
        return publicUrl+"/objects/"+id+"?ticket="+crypto.sign(json.object().put("id",id).put("owner",owner).put("kind",kind).put("expiresAt",expires));
    }
    public ObjectNode sign(String owner,String id) {
        var row=uploadRow(owner,id);
        if(!Set.of("PENDING","UPLOADING").contains(row.get("state").toString())) throw new ApiException(409,"INVALID_TRANSITION","上传已结束，不能再次写入");
        long expires=Math.min(((Number)row.get("expires_at")).longValue(),System.currentTimeMillis()+300_000);
        ObjectNode out=json.object().put("url",signedUrl(id,owner,"upload",expires)).put("urlExpiresAt",expires);
        var headers=json.object().put("Content-Type",file(owner,row.get("file_id").toString()).get("mimeType").asText());out.set("requiredHeaders",headers);return out;
    }
    public ObjectNode file(String owner,String id) {
        var rows=db.queryForList("SELECT patient_id,payload FROM files WHERE id=? AND owner_id=? AND state<>'DELETED'",id,owner);
        if(rows.isEmpty()) throw ApiException.missing();records.get(Kind.PATIENT,owner,rows.getFirst().get("patient_id").toString());return (ObjectNode)json.read(rows.getFirst().get("payload").toString());
    }
    public JsonNode readyFile(String owner,String id,String patient) {
        ObjectNode metadata=file(owner,id);
        String actual=db.queryForObject("SELECT patient_id FROM files WHERE id=? AND owner_id=?",String.class,id,owner);
        if(!patient.equals(actual)) throw new ApiException(422,"INVALID_REFERENCE","文件不属于当前患者");
        if(!"READY".equals(metadata.get("state").asText())) throw new ApiException(409,"FILE_NOT_READY","文件尚未完成校验");
        return metadata;
    }
    public ApiResult complete(Actor actor,String id,JsonNode input) {
        var row=uploadRow(actor.userId(),id);String state=row.get("state").toString();
        if(input.has("parts")&&input.get("parts").size()>0) throw ApiException.invalid("单文件上传不能提交分片");
        if("ABORTED".equals(state)) throw new ApiException(409,"INVALID_TRANSITION","上传已中止");
        String fileId=row.get("file_id").toString();ObjectNode metadata=file(actor.userId(),fileId);
        if(!"READY".equals(state)) {
            images.confirm(id,fileId,metadata);metadata.put("state","READY");
            db.update("UPDATE files SET state='READY',payload=? WHERE id=?",json.write(metadata),fileId);
            db.update("UPDATE file_uploads SET state='READY' WHERE id=?",id);
        }
        return ApiResult.ok(metadata);
    }
    public ApiResult abort(Actor actor,String id) {
        var row=uploadRow(actor.userId(),id);if("READY".equals(row.get("state"))) throw new ApiException(409,"INVALID_TRANSITION","已确认文件不能中止");
        db.update("UPDATE file_uploads SET state='ABORTED' WHERE id=?",id);return ApiResult.empty();
    }
    public ApiResult download(Actor actor,String id) {
        ObjectNode metadata=file(actor.userId(),id);if(!"READY".equals(metadata.get("state").asText())) throw new ApiException(409,"FILE_NOT_READY","文件尚未完成校验");
        long expires=System.currentTimeMillis()+120_000;
        ObjectNode out=json.object().put("url",signedUrl(id,actor.userId(),"download",expires)).put("expiresAt",expires);
        for(String key:List.of("sha256","sizeBytes","mimeType")) out.set(key,metadata.get(key));return ApiResult.ok(out);
    }
    public void put(String id,String ticket,String contentType,InputStream stream) throws IOException {
        Contract.uuid(id);JsonNode payload=crypto.verify(ticket,"upload",403,"FORBIDDEN");
        if(!id.equals(payload.path("id").asText())) throw new ApiException(403,"FORBIDDEN","上传凭证不匹配");String owner=payload.path("owner").asText();
        var initial=uploadRow(owner,id);ObjectNode metadata=file(owner,initial.get("file_id").toString());
        if(!metadata.get("mimeType").asText().equals(contentType)) throw new ApiException(415,"UNSUPPORTED_MEDIA_TYPE","Content-Type与声明不符");
        Path temporary=images.receive(id,stream,metadata.get("sizeBytes").asLong());
        try {
            tx.executeWithoutResult(s->{
                auth.lock(owner);var row=uploadRow(owner,id);
                if(!Set.of("PENDING","UPLOADING").contains(row.get("state").toString())) throw new ApiException(409,"INVALID_TRANSITION","上传已结束，不能再次写入");
                try { images.stage(temporary,id); } catch(IOException e) { throw new IllegalStateException("Cannot persist upload",e); }
                db.update("UPDATE file_uploads SET state='UPLOADING' WHERE id=?",id);
            });
        } finally { Files.deleteIfExists(temporary); }
    }
    public record Download(Path path,String mimeType,long size) {}
    public Download get(String id,String ticket) {
        Contract.uuid(id);JsonNode payload=crypto.verify(ticket,"download",403,"FORBIDDEN");
        if(!id.equals(payload.path("id").asText())) throw new ApiException(403,"FORBIDDEN","下载凭证不匹配");
        String owner=payload.path("owner").asText();
        if(db.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND status='ACTIVE'",Integer.class,owner)!=1) throw ApiException.missing();
        JsonNode metadata=file(owner,id);if(!"READY".equals(metadata.get("state").asText())) throw ApiException.missing();
        Path path=images.path(id,".image");if(!Files.isRegularFile(path)) throw new ApiException(503,"SERVER_ERROR","文件暂时不可用");
        return new Download(path,metadata.get("mimeType").asText(),metadata.get("sizeBytes").asLong());
    }
}
