package com.derm.auth;

import com.derm.api.*;
import com.derm.resource.Records.Kind;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Reading another owner's resources never grants permission to write them. */
@Component
public class AccessPolicy {
    private final JdbcTemplate db;
    /** 初始化本组件所需的依赖与配置。 */
    public AccessPolicy(JdbcTemplate db) {this.db=db;}
    /** 从数据库实时检查账号有效性及医生角色，避免旧会话保留旧权限。 */
    public boolean isDoctor(String user) {
        var rows=db.queryForList("SELECT role FROM users WHERE id=? AND status='ACTIVE'",user);
        if(rows.isEmpty()) throw new ApiException(401,"SESSION_EXPIRED","账号不可用，请重新登录");
        return "HOSPITAL_STAFF".equals(rows.getFirst().get("role"));
    }
    /** 校验当前用户对资源及有效父资源的读取权限，返回真实所有者。 */
    public String readOwner(Kind kind,String user,String id) {
        boolean doctor=isDoctor(user);
        String parent=kind==Kind.PATIENT?"":" AND EXISTS (SELECT 1 FROM patients p WHERE p.id=r.patient_id AND p.deleted_at IS NULL)";
        if(kind==Kind.CAPTURE) parent+=" AND EXISTS (SELECT 1 FROM lesions l WHERE l.id=r.lesion_id AND l.deleted_at IS NULL)";
        var rows=doctor?db.queryForList("SELECT r.owner_id FROM "+kind.table+" r WHERE r.id=? AND r.deleted_at IS NULL"+parent,id)
            :db.queryForList("SELECT r.owner_id FROM "+kind.table+" r WHERE r.id=? AND r.owner_id=? AND r.deleted_at IS NULL"+parent,id,user);
        if(rows.isEmpty()) throw ApiException.missing();return rows.getFirst().get("owner_id").toString();
    }
}
