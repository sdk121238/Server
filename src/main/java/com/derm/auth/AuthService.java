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
    /** 初始化本组件所需的依赖与配置。 */
    public AuthService(JdbcTemplate db,TransactionTemplate tx,Json json,Crypto crypto) { this.db=db;this.tx=tx;this.json=json;this.crypto=crypto; }
    /** 去除首尾空白并统一Unicode和大小写，用于账号唯一性判断。 */
    public static String normalized(String value) { return java.text.Normalizer.normalize(value.trim(),java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT); }
    /** 首次启动时预置患者账号；同名账号保持原密码与角色。 */
    public void provision(String account,String password) {
        checkPassword(password); if(account.isBlank()||account.length()>128) throw ApiException.invalid("账号长度无效");
        long now=System.currentTimeMillis(); String hash=passwords.encode(password);
        tx.executeWithoutResult(s->{
            if(!db.queryForList("SELECT id FROM users WHERE normalized_account=?",normalized(account)).isEmpty()) return;
            db.update("INSERT INTO users(id,account,normalized_account,display_name,password_hash,status,revision,generation,sync_position,created_at,updated_at) VALUES(?,?,?,?,?,'ACTIVE',1,1,0,?,?)",
                UUID.randomUUID().toString(),account,normalized(account),account,hash,now,now);
        });
    }
    /** 本机维护入口创建指定角色账号；拒绝覆盖或提升已有账号。 */
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
    /** 锁定有效账号行，使同一账号的写事务串行提交。 */
    public void lock(String user) {
        var rows=db.queryForList("SELECT status FROM users WHERE id=? FOR UPDATE",user);
        if(rows.isEmpty()||!"ACTIVE".equals(rows.getFirst().get("status"))) throw expired();
    }
    /** 读取当前有效账号的公开资料。 */
    public ObjectNode profile(String user) {
        return publicProfile(profileRow(user));
    }
    /** 读取账号资料和真实角色，拒绝停用或不存在的账号。 */
    private Map<String,Object> profileRow(String user) {
        var rows=db.queryForList("SELECT id,account,display_name,revision,role FROM users WHERE id=? AND status='ACTIVE'",user);
        if(rows.isEmpty()) throw expired();return rows.getFirst();
    }
    /** 构造不含密码的公开资料，并按真实角色附加权限标记。 */
    private ObjectNode publicProfile(Map<String,Object> row) {
        var out=json.object().put("id",row.get("id").toString()).put("account",row.get("account").toString()).put("displayName",row.get("display_name").toString()).put("role",row.get("role").toString()).putNull("organizationId");
        var permissions=json.array(); for(String type:List.of("patient","lesion","capture","file")) for(String verb:List.of("create","read","write","delete")) permissions.add(type+":"+verb);
        if("HOSPITAL_STAFF".equals(row.get("role"))) for(String type:List.of("patient","lesion","capture","file")) permissions.add(type+":read:all");
        out.set("permissions",permissions); return out;
    }
    /** 返回当前账号公开资料及资料版本ETag。 */
    public ApiResult me(Actor actor) {
        var row=profileRow(actor.userId());
        return new ApiResult(200,publicProfile(row),"\""+((Number)row.get("revision")).longValue()+"\"");
    }
    /** 校验Bearer访问令牌哈希、会话状态和有效期，返回访问者身份。 */
    public Actor authenticate(String authorization) {
        if(authorization==null||!authorization.startsWith("Bearer ")||authorization.length()>256) throw expired();
        var rows=db.queryForList("SELECT s.id,s.user_id FROM auth_sessions s JOIN users u ON u.id=s.user_id WHERE s.access_hash=? AND s.revoked_at IS NULL AND s.consumed=FALSE AND s.access_expires_at>? AND u.status='ACTIVE'",Crypto.hash(authorization.substring(7)),System.currentTimeMillis());
        if(rows.isEmpty()) throw expired(); return new Actor(rows.getFirst().get("user_id").toString(),rows.getFirst().get("id").toString());
    }
    /** 执行登录限流及密码校验，成功后创建新的令牌族。 */
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
    /** 按账号或IP维护时间窗口内的尝试次数，超限返回429。 */
    private void throttle(String key,int maximum) {
        String bucket=Crypto.hash(key); long now=System.currentTimeMillis();
        try { db.update("INSERT INTO auth_rate(bucket_id,attempts,window_start) VALUES(?,0,?)",bucket,now); } catch(DuplicateKeyException ignored) {}
        db.update("UPDATE auth_rate SET attempts=CASE WHEN window_start<? THEN 1 ELSE attempts+1 END, window_start=CASE WHEN window_start<? THEN ? ELSE window_start END WHERE bucket_id=?",now-900_000,now-900_000,now,bucket);
        int count=db.queryForObject("SELECT attempts FROM auth_rate WHERE bucket_id=?",Integer.class,bucket);
        if(count>maximum) throw new ApiException(429,"RATE_LIMITED","请求过于频繁，请稍后重试");
    }
    /** 生成不透明双令牌，只保存哈希，并返回过期时间和公开资料。 */
    private ApiResult issue(String user,String family) {
        String access=crypto.token(),refresh=crypto.token(); long now=System.currentTimeMillis(),expires=now+900_000;
        db.update("INSERT INTO auth_sessions(id,user_id,family_id,access_hash,refresh_hash,access_expires_at,expires_at,consumed,created_at) VALUES(?,?,?,?,?,?,?,FALSE,?)",
            UUID.randomUUID().toString(),user,family,Crypto.hash(access),Crypto.hash(refresh),expires,now+30L*86400_000,now);
        ObjectNode out=json.object().put("accessToken",access).put("refreshToken",refresh).put("expiresAt",expires); out.set("profile",profile(user)); return ApiResult.ok(out);
    }
    /** 轮换刷新令牌；重放已使用的令牌时撤销整个令牌族。 */
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
    /** 核对当前会话的刷新令牌并撤销对应令牌族。 */
    public ApiResult logout(Actor actor,JsonNode body) {
        return tx.execute(s->{lock(actor.userId());
            var rows=db.queryForList("SELECT family_id FROM auth_sessions WHERE refresh_hash=? AND user_id=? AND id=?",Crypto.hash(body.get("refreshToken").asText()),actor.userId(),actor.sessionId());
            if(rows.isEmpty()) throw expired();
            db.update("UPDATE auth_sessions SET revoked_at=? WHERE family_id=?",System.currentTimeMillis(),rows.getFirst().get("family_id")); return ApiResult.empty();
        });
    }
    /** 校验资料版本后修改显示名，返回新资料和ETag。 */
    public ApiResult patchProfile(Actor actor,JsonNode body,String match) {
        long revision=db.queryForObject("SELECT revision FROM users WHERE id=?",Long.class,actor.userId());
        Preconditions.check(match,revision,actor.userId());
        if(body.has("displayName")) db.update("UPDATE users SET display_name=?,revision=revision+1,updated_at=? WHERE id=?",body.get("displayName").asText(),System.currentTimeMillis(),actor.userId());
        return me(actor);
    }
    /** 验证旧密码、保存新密码哈希，并撤销其他登录会话。 */
    public ApiResult changePassword(Actor actor,JsonNode body) {
        String current=db.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,actor.userId());
        String supplied=body.get("currentPassword").asText();
        if(supplied.getBytes(StandardCharsets.UTF_8).length>72||!passwords.matches(supplied,current)) throw new ApiException(401,"INVALID_CREDENTIALS","当前密码错误");
        String next=body.get("newPassword").asText(); checkPassword(next);
        db.update("UPDATE users SET password_hash=?,updated_at=? WHERE id=?",passwords.encode(next),System.currentTimeMillis(),actor.userId());
        db.update("UPDATE auth_sessions SET revoked_at=? WHERE user_id=? AND id<>?",System.currentTimeMillis(),actor.userId(),actor.sessionId());
        return ApiResult.empty();
    }
    /** 限制密码最少字符数及BCrypt支持的UTF-8字节长度。 */
    private void checkPassword(String password) { if(password==null||password.length()<12||password.getBytes(StandardCharsets.UTF_8).length>72) throw ApiException.invalid("密码需至少12字符且UTF-8编码不超过72字节"); }
    /** 构造需要重新登录的401异常。 */
    private static ApiException expired() { return new ApiException(401,"SESSION_EXPIRED","请重新登录"); }
}
