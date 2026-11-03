package com.derm.api;

import com.derm.auth.*;
import com.derm.resource.ResourceService;
import com.derm.resource.Records.Kind;
import com.derm.storage.*;
import com.derm.sync.SyncService;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.io.*;

/** The frozen OpenAPI document drives routing, request schemas and required headers. */
@RestController
@RequestMapping("/api/v1")
public class ApiController {
    private final Contract contract;private final Json json;private final AuthService auth;private final Writes writes;
    private final ResourceService resources;private final UploadService uploads;private final SyncService sync;
    public ApiController(Contract contract,Json json,AuthService auth,Writes writes,ResourceService resources,UploadService uploads,SyncService sync) {
        this.contract=contract;this.json=json;this.auth=auth;this.writes=writes;this.resources=resources;this.uploads=uploads;this.sync=sync;
    }
    @RequestMapping("/**") public ResponseEntity<JsonNode> handle(HttpServletRequest request) throws IOException {
        String method=request.getMethod(),path=request.getRequestURI().substring(request.getContextPath().length()+7);
        var match=contract.match(method,path);var route=match.route();JsonNode op=route.operation();
        boolean isPublic=op.path("security").isEmpty();Actor actor=isPublic?null:auth.authenticate(request.getHeader("Authorization"));
        if(!"P0".equals(op.path("x-priority").asText())) throw new ApiException(501,"NOT_IMPLEMENTED","此扩展功能尚未启用");
        JsonNode body=json.object();
        if(op.has("requestBody")) {
            byte[] bytes=request.getInputStream().readNBytes(1_048_577);
            if(bytes.length>1_048_576) throw new ApiException(413,"FILE_TOO_LARGE","JSON请求超过大小限制");
            var content=op.path("requestBody").path("content").fields().next();
            String type=request.getContentType();
            if(type==null||!type.split(";")[0].equalsIgnoreCase(content.getKey())) throw new ApiException(415,"UNSUPPORTED_MEDIA_TYPE","请求Content-Type不符合合同");
            try { body=json.mapper.readTree(bytes); } catch(Exception e) { throw new ApiException(400,"VALIDATION_ERROR","JSON格式不合法"); }
            if(body==null) throw ApiException.invalid("缺少请求正文");
            contract.validate(content.getValue().get("schema"),body,"$");
            body=contract.normalize(content.getValue().get("schema"),body);
        }
        String key=null;
        for(JsonNode parameter:op.path("parameters")) {
            if("header".equals(parameter.path("in").asText())&&"Idempotency-Key".equals(parameter.path("name").asText())) {
                key=request.getHeader("Idempotency-Key");
                if(key==null||key.isBlank()||key.length()>128) throw new ApiException(400,"VALIDATION_ERROR","请提供1至128字符的Idempotency-Key");
            }
        }
        if(isPublic) return switch(path) {
            case "/system/health" -> ApiResult.ok(json.object().put("status","UP").put("serverTime",System.currentTimeMillis())).response();
            case "/auth/login" -> auth.login(body,request.getRemoteAddr()).response();
            case "/auth/refresh" -> auth.refresh(body).response();
            default -> throw ApiException.missing();
        };
        if(path.equals("/auth/logout")) return auth.logout(actor,body).response();
        final JsonNode input=body;
        var action=(java.util.function.Supplier<ApiResult>)()->dispatch(route.method()+" "+route.path(),match.params(),actor,input,request);
        String canonicalPath=route.path();
        for(var entry:match.params().entrySet()) canonicalPath=canonicalPath.replace("{"+entry.getKey()+"}",entry.getValue());
        if(Set.of("POST","PATCH","DELETE").contains(method)) return writes.run(actor,method,canonicalPath,key,body,RequestIds.id(request),action).response();
        return action.get().response();
    }
    private ApiResult dispatch(String operation,Map<String,String> p,Actor actor,JsonNode body,HttpServletRequest r) {
        String match=r.getHeader("If-Match");
        return switch(operation) {
            case "GET /auth/me" -> auth.me(actor);
            case "PATCH /users/me" -> auth.patchProfile(actor,body,match);
            case "POST /users/me/password" -> auth.changePassword(actor,body);
            case "POST /patients" -> resources.create(Kind.PATIENT,actor,body);
            case "GET /patients/{patientId}" -> resources.get(Kind.PATIENT,actor,p.get("patientId"));
            case "PATCH /patients/{patientId}" -> resources.patch(Kind.PATIENT,actor,p.get("patientId"),body,match);
            case "DELETE /patients/{patientId}" -> resources.delete(Kind.PATIENT,actor,p.get("patientId"),match);
            case "GET /patients" -> resources.list(Kind.PATIENT,actor,null,r.getParameter("q"),r.getParameter("pageToken"),limit(r,50),null,null);
            case "POST /lesions" -> resources.create(Kind.LESION,actor,body);
            case "GET /lesions/{lesionId}" -> resources.get(Kind.LESION,actor,p.get("lesionId"));
            case "PATCH /lesions/{lesionId}" -> resources.patch(Kind.LESION,actor,p.get("lesionId"),body,match);
            case "DELETE /lesions/{lesionId}" -> resources.delete(Kind.LESION,actor,p.get("lesionId"),match);
            case "GET /patients/{patientId}/lesions" -> resources.list(Kind.LESION,actor,p.get("patientId"),null,r.getParameter("pageToken"),limit(r,50),null,null);
            case "POST /captures" -> resources.create(Kind.CAPTURE,actor,body);
            case "GET /captures/{captureId}" -> resources.get(Kind.CAPTURE,actor,p.get("captureId"));
            case "PATCH /captures/{captureId}" -> resources.patch(Kind.CAPTURE,actor,p.get("captureId"),body,match);
            case "DELETE /captures/{captureId}" -> resources.delete(Kind.CAPTURE,actor,p.get("captureId"),match);
            case "GET /lesions/{lesionId}/captures" -> resources.list(Kind.CAPTURE,actor,p.get("lesionId"),null,r.getParameter("pageToken"),limit(r,50),time(r,"fromAt"),time(r,"toAt"));
            case "POST /file-uploads" -> uploads.create(actor,body);
            case "GET /file-uploads/{uploadId}" -> ApiResult.ok(uploads.upload(actor.userId(),p.get("uploadId")));
            case "POST /file-uploads/{uploadId}/sign-single" -> new ApiResult(201,uploads.sign(actor.userId(),p.get("uploadId")),null);
            case "POST /file-uploads/{uploadId}/complete" -> uploads.complete(actor,p.get("uploadId"),body);
            case "DELETE /file-uploads/{uploadId}" -> uploads.abort(actor,p.get("uploadId"));
            case "GET /files/{fileId}" -> ApiResult.ok(uploads.file(actor.userId(),p.get("fileId")));
            case "POST /files/{fileId}/download-url" -> uploads.download(actor,p.get("fileId"));
            case "POST /offline-imports" -> sync.createImport(actor,body);
            case "POST /sync/snapshots" -> sync.snapshot(actor);
            case "GET /sync/snapshots/{snapshotId}" -> sync.snapshotPage(actor,p.get("snapshotId"),r.getParameter("pageToken"),limit(r,50));
            case "GET /sync/changes" -> sync.changes(actor,r.getParameter("cursor"),limit(r,100));
            case "GET /jobs/{jobId}" -> throw ApiException.missing(); // All P0 work is synchronous; no fabricated jobs.
            case "GET /system/capabilities" -> capabilities();
            default -> throw ApiException.missing();
        };
    }
    private ApiResult capabilities() {
        var out=json.object().put("apiVersion","1.0.0").put("maxUploadBytes",ImageFiles.MAX_BYTES).put("multipartEnabled",false).put("maxSyncBatch",0).put("maxPageSize",100).put("cloudDraftsEnabled",false).put("serverAlgorithmsEnabled",false);
        out.set("acceptedMimeTypes",json.array().add("image/jpeg").add("image/png"));return ApiResult.ok(out);
    }
    private int limit(HttpServletRequest r,int fallback) { String value=r.getParameter("limit");if(value==null) return fallback;try { int parsed=Integer.parseInt(value);if(parsed<1||parsed>100) throw new NumberFormatException();return parsed; } catch(NumberFormatException e) { throw ApiException.invalid("limit范围为1至100"); } }
    private Long time(HttpServletRequest r,String name) { String value=r.getParameter(name);if(value==null) return null;try {long parsed=Long.parseLong(value);if(parsed<0) throw new NumberFormatException();return parsed;}catch(NumberFormatException e) {throw ApiException.invalid(name+"必须为非负毫秒时间");} }
}
