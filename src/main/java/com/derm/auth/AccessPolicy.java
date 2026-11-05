package com.derm.auth;

import com.derm.api.*;
import com.derm.resource.Records.Kind;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reading another owner's resources never grants permission to write them. */
@Component
public class AccessPolicy {
    private final JdbcTemplate db;
    public AccessPolicy(JdbcTemplate db) {this.db=db;}
    public boolean isDoctor(String user) {
        var rows=db.queryForList("SELECT role FROM users WHERE id=? AND status='ACTIVE'",user);
        if(rows.isEmpty()) throw new ApiException(401,"SESSION_EXPIRED","账号不可用，请重新登录");
        return "HOSPITAL_STAFF".equals(rows.getFirst().get("role"));
    }
    public String readOwner(Kind kind,String user,String id) {
        boolean doctor=isDoctor(user);
        String parent=kind==Kind.PATIENT?"":" AND EXISTS (SELECT 1 FROM patients p WHERE p.id=r.patient_id AND p.deleted_at IS NULL)";
        if(kind==Kind.CAPTURE) parent+=" AND EXISTS (SELECT 1 FROM lesions l WHERE l.id=r.lesion_id AND l.deleted_at IS NULL)";
        var rows=doctor?db.queryForList("SELECT r.owner_id FROM "+kind.table+" r WHERE r.id=? AND r.deleted_at IS NULL"+parent,id)
            :db.queryForList("SELECT r.owner_id FROM "+kind.table+" r WHERE r.id=? AND r.owner_id=? AND r.deleted_at IS NULL"+parent,id,user);
        if(rows.isEmpty()) throw ApiException.missing();return rows.getFirst().get("owner_id").toString();
    }
}
