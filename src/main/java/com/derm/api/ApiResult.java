package com.derm.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;

public record ApiResult(int status, JsonNode body, String etag) {
    /** 构造带JSON正文的200响应。 */
    public static ApiResult ok(JsonNode body) { return new ApiResult(200,body,null); }
    /** 从资源版本生成ETag，构造创建或查询响应。 */
    public static ApiResult resource(int status, JsonNode body) {
        return new ApiResult(status,body,body.has("revision") ? "\""+body.get("revision").asLong()+"\"":null);
    }
    /** 构造没有响应正文的204结果。 */
    public static ApiResult empty() { return new ApiResult(204,null,null); }
    /** 将业务结果转换成HTTP状态、响应头与JSON正文。 */
    public ResponseEntity<JsonNode> response() {
        var builder=ResponseEntity.status(status).header("Cache-Control","no-store");
        if(etag!=null) builder.header("ETag",etag);
        if(status==429) builder.header("Retry-After","900");
        return builder.body(body);
    }
}
