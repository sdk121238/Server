package com.derm.api;

import com.derm.auth.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DuplicateKeyException;
import java.util.function.Supplier;

@Service
public class Writes {
    private final JdbcTemplate db;
    private final TransactionTemplate tx;
    private final AuthService auth;
    private final Json json;
    private final ErrorHandler errors;
    public Writes(JdbcTemplate db,TransactionTemplate tx,AuthService auth,Json json,ErrorHandler errors) { this.db=db;this.tx=tx;this.auth=auth;this.json=json;this.errors=errors; }
    public ApiResult run(Actor actor,String method,String path,String key,JsonNode body,String requestId,Supplier<ApiResult> action) {
        return tx.execute(status->{
            auth.lock(actor.userId());
            // A token may have been revoked after the initial request authentication while waiting for the lock.
            var active=db.queryForList("SELECT id FROM auth_sessions WHERE id=? AND user_id=? AND revoked_at IS NULL AND consumed=FALSE AND access_expires_at>?",actor.sessionId(),actor.userId(),System.currentTimeMillis());
            if(active.isEmpty()) throw new ApiException(401,"SESSION_EXPIRED","请重新登录");
            String hash=Crypto.hash(json.write(json.canonical(body)));
            if(key!=null) {
                var rows=db.queryForList("SELECT * FROM idempotency_records WHERE owner_id=? AND request_method=? AND request_path=? AND idem_key=?",actor.userId(),method,path,key);
                if(!rows.isEmpty()) {
                    var row=rows.getFirst(); if(!hash.equals(row.get("request_hash"))) throw new ApiException(409,"IDEMPOTENCY_KEY_REUSED","相同幂等键不能用于不同请求");
                    return new ApiResult(((Number)row.get("response_status")).intValue(),row.get("response_body")==null?null:json.read(row.get("response_body").toString()),(String)row.get("etag"));
                }
            }
            Object savepoint=status.createSavepoint(); ApiResult result;
            try { result=action.get(); }
            catch(DuplicateKeyException e) {
                status.rollbackToSavepoint(savepoint); result=errors.error(new ApiException(409,"ID_CONFLICT","ID已被使用"),requestId);
            } catch(ApiException e) {
                status.rollbackToSavepoint(savepoint); if(key==null) throw e; result=errors.error(e,requestId);
            }
            status.releaseSavepoint(savepoint);
            if(key!=null&&result.status()<500) db.update("INSERT INTO idempotency_records(owner_id,request_method,request_path,idem_key,request_hash,response_status,response_body,etag,created_at) VALUES(?,?,?,?,?,?,?,?,?)",actor.userId(),method,path,key,hash,result.status(),result.body()==null?null:json.write(result.body()),result.etag(),System.currentTimeMillis());
            return result;
        });
    }
}
