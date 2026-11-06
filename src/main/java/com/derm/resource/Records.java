package com.derm.resource;

import com.derm.api.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class Records {
    public enum Kind {
        PATIENT("patients","Patient"), LESION("lesions","Lesion"), CAPTURE("captures","Capture");
        public final String table,schema;
        /** 绑定资源表名与合同Schema名称。 */
        Kind(String table,String schema) { this.table=table;this.schema=schema; }
    }
    public record Row(String id,String owner,String patient,long revision,ObjectNode data) {}
    private final JdbcTemplate db;
    private final Json json;
    private final com.derm.auth.AccessPolicy access;
    /** 初始化本组件所需的依赖与配置。 */
    public Records(JdbcTemplate db,Json json,com.derm.auth.AccessPolicy access) { this.db=db;this.json=json;this.access=access; }
    /** 应用患者或医生读取策略后，读取真实所有者的资源。 */
    public Row read(Kind kind,String user,String id) {return get(kind,access.readOwner(kind,user,id),id);}
    /** 读取本人拥有且未删除的记录，供受所有权限制的写操作使用。 */
    public Row get(Kind kind,String owner,String id) {
        var rows=db.queryForList("SELECT * FROM "+kind.table+" WHERE id=? AND owner_id=? AND deleted_at IS NULL",id,owner);
        if(rows.isEmpty()) throw ApiException.missing(); var row=rows.getFirst();
        return new Row(id,owner,kind==Kind.PATIENT?id:row.get("patient_id").toString(),((Number)row.get("revision")).longValue(),(ObjectNode)json.read(row.get("payload").toString()));
    }
    /** 检查UUID是否曾被使用，防止重新创建软删除记录。 */
    public boolean exists(Kind kind,String id) { return !db.queryForList("SELECT id FROM "+kind.table+" WHERE id=?",id).isEmpty(); }
    /** 递增账号同步位置并写入变更事件，调用方须持有账号锁。 */
    public void event(String owner,Kind kind,ObjectNode data,String operation) {
        // Caller holds users(owner) FOR UPDATE until commit. Every event position is commit ordered within this scope.
        db.update("UPDATE users SET sync_position=sync_position+1 WHERE id=?",owner);
        long position=db.queryForObject("SELECT sync_position FROM users WHERE id=?",Long.class,owner);
        var change=json.object().put("entityType",kind.name()).put("entityId",data.get("id").asText()).put("operation",operation).put("revision",data.get("revision").asLong());
        change.set("payload","UPSERT".equals(operation)?data:null);
        db.update("INSERT INTO sync_changes(owner_id,position,payload,created_at) VALUES(?,?,?,?)",owner,position,json.write(change),System.currentTimeMillis());
    }
    /** 保存采集记录的完整版本快照、内容哈希与操作人。 */
    public void history(String actor,ObjectNode data) {
        db.update("INSERT INTO capture_revisions(capture_id,revision,content_revision,content_hash,payload,actor_id,created_at) VALUES(?,?,?,?,?,?,?)",
            data.get("id").asText(),data.get("revision").asLong(),data.get("contentRevision").asLong(),
            com.derm.auth.Crypto.hash(json.write(json.canonical(data))),json.write(data),actor,System.currentTimeMillis());
    }
    /** 读取当前账号未删除资源，供个人同步及级联墓碑处理使用。 */
    public List<ObjectNode> all(Kind kind,String owner) {
        return db.query("SELECT payload FROM "+kind.table+" WHERE owner_id=? AND deleted_at IS NULL ORDER BY id",(rs,n)->(ObjectNode)json.read(rs.getString(1)),owner);
    }
    /** 更新资源版本、删除状态和JSON，患者同时更新独立查询列。 */
    public void update(Kind kind,String owner,ObjectNode data) {
        db.update("UPDATE "+kind.table+" SET revision=?,deleted_at=?,payload=? WHERE id=? AND owner_id=?",data.get("revision").asLong(),data.path("deletedAt").isNumber()?data.get("deletedAt").asLong():null,json.write(data),data.get("id").asText(),owner);
        if(kind==Kind.PATIENT) patientColumns(data);
    }
    /** 将患者JSON中的姓名、资料和医生关联同步到独立列。 */
    public void patientColumns(ObjectNode data) {
        db.update("UPDATE patients SET patient_name=?,phone_number=?,gender=?,birth_year_month=?,attending_doctor_id=? WHERE id=?",
            data.path("name").asText(),text(data,"phoneNumber"),text(data,"gender"),text(data,"birthYearMonth"),text(data,"attendingDoctorId"),data.get("id").asText());
    }
    /** 读取可空JSON文本字段，缺失或null转换为数据库NULL。 */
    private static String text(ObjectNode data,String key) {return data.hasNonNull(key)?data.get(key).asText():null;}
}
