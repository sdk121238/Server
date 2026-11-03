package com.derm.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;

public record ApiResult(int status, JsonNode body, String etag) {
    public static ApiResult ok(JsonNode body) { return new ApiResult(200,body,null); }
    public static ApiResult resource(int status, JsonNode body) {
        return new ApiResult(status,body,body.has("revision") ? "\""+body.get("revision").asLong()+"\"":null);
    }
    public static ApiResult empty() { return new ApiResult(204,null,null); }
    public ResponseEntity<JsonNode> response() {
        var builder=ResponseEntity.status(status).header("Cache-Control","no-store");
        if(etag!=null) builder.header("ETag",etag);
        if(status==429) builder.header("Retry-After","900");
        return builder.body(body);
    }
}
