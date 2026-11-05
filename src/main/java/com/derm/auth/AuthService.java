package com.derm.auth;

import com.derm.api.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.dao.DuplicateKeyException;
import java.util.*;
import java.nio.charset.StandardCharsets;

@Service
public class AuthService {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final Json json;
    private final Crypto crypto;
    private final BCryptPasswordEncoder passwords=new BCryptPasswordEncoder(12);
    private final String dummy=passwords.encode("nonexistent-account-timing-placeholder");
    public AuthService(JdbcTemplate db,TransactionTemplate tx,Json json,Crypto crypto) { this.db=db;this.tx=tx;this.json=json;this.crypto=crypto; }
    public static String normalized(String value) { return java.text.Normalizer.normalize(value.trim(),java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT); }
    public void provision(String account,String password) {
        checkPassword(password); if(account.isBlank()||account.length()>128) throw ApiException.invalid("账号长度无效");
        long now=System.currentTimeMillis(); String hash=passwords.encode(password);
        tx.executeWithoutResult(s->{
            if(!db.queryForList("SELECT id FROM users WHERE normalized_account=?",normalized(account)).isEmpty()) return;
            db.update("INSERT INTO users(id,account,normalized_account,display_name,password_hash,status,revision,generation,sync_position,created_at,updated_at) VALUES(?,?,?,?,?,'ACTIVE',1,1,0,?,?)",
                UUID.randomUUID().toString(),account,normalized(account),account,hash,now,now);
        });
    }
    /** Local maintenance only. Existing accounts are never overwritten or promoted. */
    public String createAccount(String account,String password,String displayName,String role) {
        checkPassword(password);
        if(account==null||account.isBlank()||account.length()>128||displayName==null||displayName.isBlank()||displayName.length()>128||!Set.of("PATIENT","HOSPITAL_STAFF").contains(role)) throw ApiException.invalid("账号、显示名或角色无效");
        String id=UUID.randomUUID().toString(),hash=passwords.encode(password);long now=System.currentTimeMillis();
        try {
            db.update("INSERT INTO users(id,account,normalized_account,display_name,password_hash,status,revision,generation,sync_position,created_at,updated_at,role) VALUES(?,?,?,?,?,'ACTIVE',1,1,0,?,?,?)",
                id,account.trim(),normalized(account),displayName.trim(),hash,now,now,role);
        } catch(DuplicateKeyException e) {throw new ApiException(409,"ACCOUNT_EXISTS","账号已存在，不会覆盖密码或更改角色");}
        return id;
    }
    public void lock(String user) {
        var rows=db.queryForList("SELECT status FROM users WHERE id=? FOR UPDATE",user);
        if(rows.isEmpty()||!"ACTIVE".equals(rows.getFirst().get("status"))) throw expired();
    }
    public ObjectNode profile(String user) {
        return publicProfile(profileRow(user));
    }
    private Map<String,Object> profileRow(String user) {
        var rows=db.queryForList("SELECT id,account,display_name,revision,role FROM users WHERE id=? AND status='ACTIVE'",user);
        if(rows.isEmpty()) throw expired();return rows.getFirst();
    }
    private ObjectNode publicProfile(Map<String,Object> row) {
        var out=json.object().put("id",row.get("id").toString()).put("account",row.get("account").toString()).put("displayName",row.get("display_name").toString()).put("role",row.get("role").toString()).putNull("organizationId");
        var permissions=json.array(); for(String type:List.of("patient","lesion","capture","file")) for(String verb:List.of("create","read","write","delete")) permissions.add(type+":"+verb);
        if("HOSPITAL_STAFF".equals(row.get("role"))) for(String type:List.of("patient","lesion","capture","file")) permissions.add(type+":read:all");
        out.set("permissions",permissions); return out;
    }
    public ApiResult me(Actor actor) {
        var row=profileRow(actor.userId());
        return new ApiResult(200,publicProfile(row),"\""+((Number)row.get("revision")).longValue()+"\"");
    }
    public Actor authenticate(String authorization) {
        if(authorization==null||!authorization.startsWith("Bearer ")||authorization.length()>256) throw expired();
        var rows=db.queryForList("SELECT s.id,s.user_id FROM auth_sessions s JOIN users u ON u.id=s.user_id WHERE s.access_hash=? AND s.revoked_at IS NULL AND s.consumed=FALSE AND s.access_expires_at>? AND u.status='ACTIVE'",Crypto.hash(authorization.substring(7)),System.currentTimeMillis());
        if(rows.isEmpty()) throw expired(); return new Actor(rows.getFirst().get("user_id").toString(),rows.getFirst().get("id").toString());
    }
    public ApiResult login(JsonNode body,String ip) {
        throttle("login-ip:"+ip,100); throttle("login-account:"+normalized(body.get("account").asText()),20);
        return tx.execute(s->{
            var rows=db.queryForList("SELECT * FROM users WHERE normalized_account=? FOR UPDATE",normalized(body.get("account").asText()));
            String password=body.get("password").asText();
            boolean matches=password.getBytes(StandardCharsets.UTF_8).length<=72&&passwords.matches(password,rows.isEmpty()?dummy:rows.getFirst().get("password_hash").toString());
            if(rows.isEmpty()||!matches||!"ACTIVE".equals(rows.getFirst().get("status"))) throw new ApiException(401,"INVALID_CREDENTIALS","账号或密码错误");
            return issue(rows.getFirst().get("id").toString(),UUID.randomUUID().toString());
        });
    }
    private void throttle(String key,int maximum) {
        String bucket=Crypto.hash(key); long now=System.currentTimeMillis();
        try { db.update("INSERT INTO auth_rate(bucket_id,attempts,window_start) VALUES(?,0,?)",bucket,now); } catch(DuplicateKeyException ignored) {}
        db.update("UPDATE auth_rate SET attempts=CASE WHEN window_start<? THEN 1 ELSE attempts+1 END, window_start=CASE WHEN window_start<? THEN ? ELSE window_start END WHERE bucket_id=?",now-900_000,now-900_000,now,bucket);
        int count=db.queryForObject("SELECT attempts FROM auth_rate WHERE bucket_id=?",Integer.class,bucket);
        if(count>maximum) throw new ApiException(429,"RATE_LIMITED","请求过于频繁，请稍后重试");
    }
    private ApiResult issue(String user,String family) {
        String access=crypto.token(),refresh=crypto.token(); long now=System.currentTimeMillis(),expires=now+900_000;
        db.update("INSERT INTO auth_sessions(id,user_id,family_id,access_hash,refresh_hash,access_expires_at,expires_at,consumed,created_at) VALUES(?,?,?,?,?,?,?,FALSE,?)",
            UUID.randomUUID().toString(),user,family,Crypto.hash(access),Crypto.hash(refresh),expires,now+30L*86400_000,now);
        ObjectNode out=json.object().put("accessToken",access).put("refreshToken",refresh).put("expiresAt",expires); out.set("profile",profile(user)); return ApiResult.ok(out);
    }
    public ApiResult refresh(JsonNode body) {
        var result=tx.execute(s->{
            var rows=db.queryForList("SELECT * FROM auth_sessions WHERE refresh_hash=?",Crypto.hash(body.get("refreshToken").asText()));
            if(rows.isEmpty()) return (Object)expired();
            String user=rows.getFirst().get("user_id").toString(); lock(user);
            var row=db.queryForList("SELECT * FROM auth_sessions WHERE refresh_hash=? FOR UPDATE",Crypto.hash(body.get("refreshToken").asText())).getFirst();
            String family=row.get("family_id").toString();
            if(Boolean.TRUE.equals(row.get("consumed"))) {
                db.update("UPDATE auth_sessions SET revoked_at=? WHERE family_id=?",System.currentTimeMillis(),family);
                return (Object)expired(); // Commit revocation before returning 401.
            }
            if(row.get("revoked_at")!=null||((Number)row.get("expires_at")).longValue()<=System.currentTimeMillis()) return (Object)expired();
            db.update("UPDATE auth_sessions SET consumed=TRUE WHERE id=?",row.get("id")); return issue(user,family);
        });
        if(result instanceof ApiException e) throw e; return (ApiResult)result;
    }
    public ApiResult logout(Actor actor,JsonNode body) {
        return tx.execute(s->{lock(actor.userId());
            var rows=db.queryForList("SELECT family_id FROM auth_sessions WHERE refresh_hash=? AND user_id=? AND id=?",Crypto.hash(body.get("refreshToken").asText()),actor.userId(),actor.sessionId());
            if(rows.isEmpty()) throw expired();
            db.update("UPDATE auth_sessions SET revoked_at=? WHERE family_id=?",System.currentTimeMillis(),rows.getFirst().get("family_id")); return ApiResult.empty();
        });
    }
    public ApiResult patchProfile(Actor actor,JsonNode body,String match) {
        long revision=db.queryForObject("SELECT revision FROM users WHERE id=?",Long.class,actor.userId());
        Preconditions.check(match,revision,actor.userId());
        if(body.has("displayName")) db.update("UPDATE users SET display_name=?,revision=revision+1,updated_at=? WHERE id=?",body.get("displayName").asText(),System.currentTimeMillis(),actor.userId());
        return me(actor);
    }
    public ApiResult changePassword(Actor actor,JsonNode body) {
        String current=db.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,actor.userId());
        String supplied=body.get("currentPassword").asText();
        if(supplied.getBytes(StandardCharsets.UTF_8).length>72||!passwords.matches(supplied,current)) throw new ApiException(401,"INVALID_CREDENTIALS","当前密码错误");
        String next=body.get("newPassword").asText(); checkPassword(next);
        db.update("UPDATE users SET password_hash=?,updated_at=? WHERE id=?",passwords.encode(next),System.currentTimeMillis(),actor.userId());
        db.update("UPDATE auth_sessions SET revoked_at=? WHERE user_id=? AND id<>?",System.currentTimeMillis(),actor.userId(),actor.sessionId());
        return ApiResult.empty();
    }
    private void checkPassword(String password) { if(password==null||password.length()<12||password.getBytes(StandardCharsets.UTF_8).length>72) throw ApiException.invalid("密码需至少12字符且UTF-8编码不超过72字节"); }
    private static ApiException expired() { return new ApiException(401,"SESSION_EXPIRED","请重新登录"); }
}
