package com.derm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.security.MessageDigest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendIntegrationTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired com.derm.api.Writes writes;
    @Autowired com.derm.auth.AuthService auth;
    @Autowired com.derm.resource.ResourceService resources;

    /** 验证健康接口公开且只返回最小健康信息。 */
    @Test void healthIsPublicAndMinimal() throws Exception {
        http.perform(get("/api/v1/system/health")).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP")).andExpect(jsonPath("$.serverTime").isNumber())
            .andExpect(header().exists("X-Request-Id"));
    }

    /** 验证登录提示不能将普通患者提升为医生。 */
    @Test void loginDoesNotGrantRequestedHospitalRole() throws Exception {
        JsonNode tokens = login();
        assertThat(tokens.at("/profile/role").asText()).isEqualTo("PATIENT");
        assertThat(tokens.has("data")).isFalse();
        http.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokens.get("accessToken").asText()))
            .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""))
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    /** 验证旧刷新令牌重放会撤销整个令牌族。 */
    @Test void refreshReplayRevokesEntireFamily() throws Exception {
        JsonNode first = login();
        JsonNode second = body(http.perform(post("/api/v1/auth/refresh").contentType("application/json")
            .content(json.writeValueAsBytes(java.util.Map.of("refreshToken", first.get("refreshToken").asText()))))
            .andExpect(status().isOk()).andReturn());
        http.perform(post("/api/v1/auth/refresh").contentType("application/json")
            .content(json.writeValueAsBytes(java.util.Map.of("refreshToken", first.get("refreshToken").asText()))))
            .andExpect(status().isUnauthorized());
        http.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + second.get("accessToken").asText()))
            .andExpect(status().isUnauthorized());
    }

    /** 验证错误凭据及匿名访问受保护接口均被拒绝。 */
    @Test void invalidCredentialsAndAnonymousAccessAreRejected() throws Exception {
        http.perform(post("/api/v1/auth/login").contentType("application/json")
            .content("{\"account\":\"test-owner\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        http.perform(get("/api/v1/patients")).andExpect(status().isUnauthorized());
    }

    /** 验证账号隔离、幂等重试及乐观锁冲突。 */
    @Test void ownershipIdempotencyAndOptimisticLocking() throws Exception {
        String token = token(); String id = UUID.randomUUID().toString(); String key = UUID.randomUUID().toString();
        Map<String,Object> patient = Map.of("id",id,"name","随访患者");
        http.perform(post("/api/v1/patients").header("Authorization",bearer(token)).header("Idempotency-Key",key)
            .contentType("application/json").content(json.writeValueAsBytes(patient)))
            .andExpect(status().isCreated()).andExpect(header().string("ETag","\"1\""));
        http.perform(post("/api/v1/patients").header("Authorization",bearer(token)).header("Idempotency-Key",key)
            .contentType("application/json").content(json.writeValueAsBytes(patient)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.revision").value(1));
        http.perform(post("/api/v1/patients").header("Authorization",bearer(token)).header("Idempotency-Key",key)
            .contentType("application/json").content(json.writeValueAsBytes(Map.of("id",id,"name","changed"))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        http.perform(patch("/api/v1/patients/"+id).header("Authorization",bearer(token))
            .contentType("application/merge-patch+json").content("{\"name\":\"修改\"}"))
            .andExpect(status().is(428));
        http.perform(patch("/api/v1/patients/"+id).header("Authorization",bearer(token)).header("If-Match","\"1\"")
            .contentType("application/merge-patch+json").content("{\"name\":\"修改\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2));
        http.perform(patch("/api/v1/patients/"+id).header("Authorization",bearer(token)).header("If-Match","\"1\"")
            .contentType("application/merge-patch+json").content("{\"name\":\"过期\"}"))
            .andExpect(status().isPreconditionFailed());
        String other = otherToken();
        http.perform(get("/api/v1/patients/"+id).header("Authorization",bearer(other))).andExpect(status().isNotFound());
        http.perform(post("/api/v1/lesions").header("Authorization",bearer(other)).header("Idempotency-Key",UUID.randomUUID())
            .contentType("application/json").content(json.writeValueAsBytes(Map.of("id",UUID.randomUUID(),"patientId",id,"bodySite","手"))))
            .andExpect(status().isNotFound());
    }

    /** 验证快照冻结和级联删除事件的顺序。 */
    @Test void frozenSnapshotAndCascadeDeletionProduceOrderedChanges() throws Exception {
        String token = token(); String patient = create(token,"/patients",Map.of("id",UUID.randomUUID(),"name","同步患者")).get("id").asText();
        String lesion = create(token,"/lesions",Map.of("id",UUID.randomUUID(),"patientId",patient,"bodySite","手背")).get("id").asText();
        JsonNode snapshot = create(token,"/sync/snapshots",null);
        String lateId = create(token,"/patients",Map.of("id",UUID.randomUUID(),"name","快照后新增")).get("id").asText();
        String page = "/api/v1/sync/snapshots/"+snapshot.get("id").asText()+"?limit=1"; String cursor=null;
        List<String> snapshotIds = new ArrayList<>();
        for(int i=0;i<100;i++) {
            JsonNode result = body(http.perform(get(page).header("Authorization",bearer(token))).andExpect(status().isOk()).andReturn());
            result.get("items").forEach(n->snapshotIds.add(n.get("entityId").asText()));
            if(result.get("nextPageToken").isNull()) { cursor=result.get("nextCursor").asText(); break; }
            page="/api/v1/sync/snapshots/"+snapshot.get("id").asText()+"?limit=1&pageToken="+result.get("nextPageToken").asText();
        }
        assertThat(snapshotIds).contains(patient,lesion).doesNotContain(lateId);
        http.perform(delete("/api/v1/patients/"+patient).header("Authorization",bearer(token)).header("If-Match","\"1\""))
            .andExpect(status().isNoContent());
        JsonNode changes = body(http.perform(get("/api/v1/sync/changes").param("cursor",cursor)
            .header("Authorization",bearer(token))).andExpect(status().isOk()).andReturn());
        List<String> deleted = new ArrayList<>();
        changes.get("items").forEach(n->{if(n.get("operation").asText().equals("DELETE")) {deleted.add(n.get("entityId").asText()); assertThat(n.get("payload").isNull()).isTrue();}});
        assertThat(deleted).contains(patient,lesion);
        http.perform(get("/api/v1/sync/changes").param("cursor",cursor).header("Authorization",bearer(otherToken())))
            .andExpect(status().isGone());
    }

    /** 验证真实图片上传、采集创建及嵌套补丁校验。 */
    @Test void actualImageUploadCaptureAndMergePatchValidation() throws Exception {
        String token = token();
        String patient = create(token,"/patients",Map.of("id",UUID.randomUUID(),"name","图片患者")).get("id").asText();
        String lesion = create(token,"/lesions",Map.of("id",UUID.randomUUID(),"patientId",patient,"bodySite","手背")).get("id").asText();
        ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB),"png",out);
        byte[] png=out.toByteArray(); String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png));
        JsonNode upload = create(token,"/file-uploads",Map.of("patientId",patient,"sizeBytes",png.length,"sha256",hash,"mimeType","image/png","width",20,"height",20,"strategy","SINGLE"));
        String fileId=upload.get("fileId").asText(); String uploadId=upload.get("id").asText();
        Map<String,Object> capture = new HashMap<>(Map.of("id",UUID.randomUUID(),"lesionId",lesion,"imageFileId",fileId,"capturedAt",1,"deviceModel","test","source","CAMERA","assetKind","CAPTURE_IMAGE","referenceOnly",false));
        http.perform(post("/api/v1/captures").header("Authorization",bearer(token)).header("Idempotency-Key",UUID.randomUUID())
            .contentType("application/json").content(json.writeValueAsBytes(capture)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FILE_NOT_READY"));
        java.net.URI uri=java.net.URI.create(upload.get("uploadUrl").asText());
        http.perform(put(uri.getPath()+"?"+uri.getRawQuery()).contentType("image/png").content(png)).andExpect(status().isNoContent());
        http.perform(post("/api/v1/file-uploads/"+uploadId+"/complete").header("Authorization",bearer(token))
            .header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("READY"));
        http.perform(put(uri.getPath()+"?"+uri.getRawQuery()).contentType("image/png").content(png)).andExpect(status().isConflict());
        capture.put("calibration",Map.of("markerId","test","markerWidthMm",10,"pixelsPerMm",2,"qualityScore",1));
        capture.put("annotation",Map.of("points",List.of(Map.of("x",0,"y",0),Map.of("x",10,"y",0),Map.of("x",10,"y",10),Map.of("x",0,"y",10)),"imageWidth",20,"imageHeight",20,"edited",true));
        capture.put("analysis",Map.of("areaMm2",25,"algorithmVersion","manual","isValid",true,"colorDifference",5));
        String captureId = create(token,"/captures",capture).get("id").asText();
        http.perform(patch("/api/v1/captures/"+captureId).header("Authorization",bearer(token)).header("If-Match","\"1\"")
            .contentType("application/merge-patch+json").content("{\"analysis\":{\"colorDifference\":6}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.analysis.areaMm2").value(25)).andExpect(jsonPath("$.analysis.colorDifference").value(6));
        http.perform(patch("/api/v1/captures/"+captureId).header("Authorization",bearer(token)).header("If-Match","\"2\"")
            .contentType("application/merge-patch+json").content("{\"calibration\":{\"pixelsPerMm\":3}}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.analysis").isEmpty()).andExpect(jsonPath("$.contentRevision").value(3));
        http.perform(patch("/api/v1/captures/"+captureId).header("Authorization",bearer(token)).header("If-Match","\"3\"")
            .contentType("application/merge-patch+json").content("{\"ownerUserId\":\"attacker\"}"))
            .andExpect(status().isUnprocessableEntity());
        JsonNode download=body(http.perform(post("/api/v1/files/"+fileId+"/download-url").header("Authorization",bearer(token)))
            .andExpect(status().isOk()).andReturn());
        uri=java.net.URI.create(download.get("url").asText());
        byte[] received=http.perform(get(uri.getPath()+"?"+uri.getRawQuery())).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(received).isEqualTo(png);
        http.perform(get("/api/v1/files/"+fileId).header("Authorization",bearer(otherToken()))).andExpect(status().isNotFound());
    }

    /** 验证并发重试只提交一个资源和一条同步事件。 */
    @Test void concurrentRetriesCommitOneResourceAndOneSyncEvent() throws Exception {
        String token=token(),id=UUID.randomUUID().toString(),key=UUID.randomUUID().toString();
        byte[] request=json.writeValueAsBytes(Map.of("id",id,"name","并发重试"));
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var calls=new ArrayList<java.util.concurrent.Future<MvcResult>>();
            for(int i=0;i<4;i++) calls.add(pool.submit(()->http.perform(post("/api/v1/patients").header("Authorization",bearer(token))
                .header("Idempotency-Key",key).contentType("application/json").content(request)).andReturn()));
            for(var call:calls) assertThat(call.get().getResponse().getStatus()).isEqualTo(201);
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM patients WHERE id=?",Integer.class,id)).isEqualTo(1);
        int matches=0;
        for(String payload:db.queryForList("SELECT payload FROM sync_changes",String.class)) if(json.readTree(payload).get("entityId").asText().equals(id)) matches++;
        assertThat(matches).isEqualTo(1);
    }

    /** 验证退出后访问与刷新令牌均失效。 */
    @Test void logoutRevokesAccessAndRefresh() throws Exception {
        JsonNode first=login();String access=first.get("accessToken").asText(),refresh=first.get("refreshToken").asText();
        http.perform(post("/api/v1/auth/logout").header("Authorization",bearer(access)).contentType("application/json")
            .content(json.writeValueAsBytes(Map.of("refreshToken",refresh)))).andExpect(status().isNoContent());
        http.perform(get("/api/v1/auth/me").header("Authorization",bearer(access))).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/auth/refresh").contentType("application/json").content(json.writeValueAsBytes(Map.of("refreshToken",refresh))))
            .andExpect(status().isUnauthorized());
    }

    /** 验证导入范围受限且失败写入不会残留资源。 */
    @Test void importPlanRestrictsPatientIdsAndFailedWriteRollsBack() throws Exception {
        String token=token(),allowed=UUID.randomUUID().toString(),denied=UUID.randomUUID().toString();
        JsonNode plan=create(token,"/offline-imports",Map.of("clientDatasetId",UUID.randomUUID(),"confirmedPatientIds",List.of(allowed)));
        String key=UUID.randomUUID().toString();
        byte[] bad=json.writeValueAsBytes(Map.of("id",denied,"name","越界导入","importId",plan.get("id").asText()));
        for(int i=0;i<2;i++) http.perform(post("/api/v1/patients").header("Authorization",bearer(token)).header("Idempotency-Key",key)
            .contentType("application/json").content(bad)).andExpect(status().isForbidden());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM patients WHERE id=?",Integer.class,denied)).isZero();
        create(token,"/patients",Map.of("id",allowed,"name","确认导入","importId",plan.get("id").asText()));
    }

    /** 验证跨账号游标及不合法合同字段被拒绝。 */
    @Test void crossAccountPaginationAndSchemaErrorsAreRejected() throws Exception {
        String token=token();
        create(token,"/patients",Map.of("id",UUID.randomUUID(),"name","分页甲"));
        create(token,"/patients",Map.of("id",UUID.randomUUID(),"name","分页乙"));
        JsonNode page=body(http.perform(get("/api/v1/patients?limit=1").header("Authorization",bearer(token))).andExpect(status().isOk()).andReturn());
        http.perform(get("/api/v1/patients").param("pageToken",page.get("nextPageToken").asText()).header("Authorization",bearer(otherToken())))
            .andExpect(status().isGone());
        http.perform(get("/api/v1/patients?limit=101").header("Authorization",bearer(token))).andExpect(status().isUnprocessableEntity());
        http.perform(patch("/api/v1/users/me").header("Authorization",bearer(token)).header("If-Match","\"1\"")
            .contentType("application/merge-patch+json").content("{\"role\":\"HOSPITAL_STAFF\"}"))
            .andExpect(status().isUnprocessableEntity());
        http.perform(post("/api/v1/patients").header("Authorization",bearer(token)).header("Idempotency-Key",UUID.randomUUID())
            .contentType("application/json").content(json.writeValueAsBytes(Map.of("id",UUID.randomUUID(),"name","日期","birthDate","2026-02-30"))))
            .andExpect(status().isUnprocessableEntity());
    }

    /** 验证含重复字段的JSON请求被拒绝。 */
    @Test void duplicateJsonMembersAreRejected() throws Exception {
        String token=token();
        http.perform(post("/api/v1/patients").header("Authorization",bearer(token)).header("Idempotency-Key",UUID.randomUUID())
            .contentType("application/json").content("{\"id\":\""+UUID.randomUUID()+"\",\"name\":\"first\",\"name\":\"second\"}"))
            .andExpect(status().isBadRequest());
    }

    /** 验证UUID大小写规范化且不会重复创建同一资源。 */
    @Test void mixedCaseUuidAddressesSameResourceAndCannotDuplicateIt() throws Exception {
        String token=token(),id=UUID.randomUUID().toString();
        create(token,"/patients",Map.of("id",id,"name","UUID身份"));
        http.perform(get("/api/v1/patients/"+id.toUpperCase(Locale.ROOT)).header("Authorization",bearer(token)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        http.perform(post("/api/v1/patients").header("Authorization",bearer(token)).header("Idempotency-Key",UUID.randomUUID())
            .contentType("application/json").content(json.writeValueAsBytes(Map.of("id",id.toUpperCase(Locale.ROOT),"name","duplicate"))))
            .andExpect(status().isConflict());
    }

    /** 验证同步游标不会跳过尚未提交的早期事务。 */
    @Test void uncommittedSyncPositionsCannotHideEarlierTransactions() throws Exception {
        String token=otherToken();var actor=auth.authenticate(bearer(token));
        JsonNode snapshot=create(token,"/sync/snapshots",null);
        String cursor=body(http.perform(get("/api/v1/sync/snapshots/"+snapshot.get("id").asText()).header("Authorization",bearer(token)))
            .andExpect(status().isOk()).andReturn()).get("nextCursor").asText();
        String firstId=UUID.randomUUID().toString(),secondId=UUID.randomUUID().toString();
        var firstBody=json.valueToTree(Map.of("id",firstId,"name","先开始的事务"));
        var written=new java.util.concurrent.CountDownLatch(1);var commit=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->writes.run(actor,"POST","/patients",UUID.randomUUID().toString(),firstBody,"concurrency-test",()->{
                var result=resources.create(com.derm.resource.Records.Kind.PATIENT,actor,firstBody);written.countDown();
                try { if(!commit.await(10,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("commit latch timed out"); }
                catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException(e);} return result;
            }));
            try {
                assertThat(written.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                var second=pool.submit(()->create(token,"/patients",Map.of("id",secondId,"name","后开始的事务")));
                JsonNode pending=body(http.perform(get("/api/v1/sync/changes").param("cursor",cursor).header("Authorization",bearer(token))).andExpect(status().isOk()).andReturn());
                assertThat(pending.get("items").isEmpty()).isTrue();
                commit.countDown();assertThat(first.get().status()).isEqualTo(201);second.get();
                JsonNode changes=body(http.perform(get("/api/v1/sync/changes").param("cursor",cursor).header("Authorization",bearer(token))).andExpect(status().isOk()).andReturn());
                assertThat(changes.get("items").size()).isEqualTo(2);
                assertThat(changes.at("/items/0/entityId").asText()).isEqualTo(firstId);
                assertThat(changes.at("/items/1/entityId").asText()).isEqualTo(secondId);
            } finally {commit.countDown();}
        }
    }

    /** 验证上传失败、签名过期及跨患者图片引用被拒绝。 */
    @Test void uploadFailureExpiryAndCrossPatientReferences() throws Exception {
        String token=token(),patient=create(token,"/patients",Map.of("id",UUID.randomUUID(),"name","校验患者")).get("id").asText();
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB),"png",out);byte[] png=out.toByteArray();
        JsonNode upload=create(token,"/file-uploads",Map.of("patientId",patient,"sizeBytes",png.length,"sha256","0".repeat(64),"mimeType","image/png","width",20,"height",20,"strategy","SINGLE"));
        java.net.URI uri=java.net.URI.create(upload.get("uploadUrl").asText());
        http.perform(put(uri.getPath()+"?"+uri.getRawQuery()).contentType("image/png").content(png)).andExpect(status().isNoContent());
        http.perform(post("/api/v1/file-uploads/"+upload.get("id").asText()+"/complete").header("Authorization",bearer(token)).header("Idempotency-Key",UUID.randomUUID())
            .contentType("application/json").content("{}")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CHECKSUM_MISMATCH"));
        http.perform(get("/api/v1/files/"+upload.get("fileId").asText()).header("Authorization",bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("PENDING"));
        db.update("UPDATE file_uploads SET expires_at=1 WHERE id=?",upload.get("id").asText());
        http.perform(get("/api/v1/file-uploads/"+upload.get("id").asText()).header("Authorization",bearer(token))).andExpect(status().isGone());
        String otherPatient=create(token,"/patients",Map.of("id",UUID.randomUUID(),"name","另一患者")).get("id").asText();
        String lesion=create(token,"/lesions",Map.of("id",UUID.randomUUID(),"patientId",otherPatient,"bodySite","手")).get("id").asText();
        http.perform(post("/api/v1/captures").header("Authorization",bearer(token)).header("Idempotency-Key",UUID.randomUUID()).contentType("application/json")
            .content(json.writeValueAsBytes(Map.of("id",UUID.randomUUID(),"lesionId",lesion,"imageFileId",upload.get("fileId").asText(),"capturedAt",1,"deviceModel","test","source","UNKNOWN","assetKind","UNKNOWN","referenceOnly",false))))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_REFERENCE"));
    }

    /** 验证并发刷新重放连同获胜会话一起撤销。 */
    @Test void simultaneousRefreshReplayRevokesTheWinningSession() throws Exception {
        JsonNode tokens=login();byte[] request=json.writeValueAsBytes(Map.of("refreshToken",tokens.get("refreshToken").asText()));
        List<MvcResult> results=new ArrayList<>();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->http.perform(post("/api/v1/auth/refresh").contentType("application/json").content(request)).andReturn());
            var second=pool.submit(()->http.perform(post("/api/v1/auth/refresh").contentType("application/json").content(request)).andReturn());
            results.add(first.get());results.add(second.get());
        }
        assertThat(results.stream().map(r->r.getResponse().getStatus()).sorted().toList()).containsExactly(200,401);
        for(var result:results) if(result.getResponse().getStatus()==200) http.perform(get("/api/v1/auth/me")
            .header("Authorization",bearer(body(result).get("accessToken").asText()))).andExpect(status().isUnauthorized());
    }

    /** 验证修改密码撤销其他会话且资料修改受ETag保护。 */
    @Test void passwordChangeRevokesOtherSessionsAndProfileUsesEtag() throws Exception {
        String first=otherToken();JsonNode profile=body(http.perform(get("/api/v1/auth/me").header("Authorization",bearer(first))).andExpect(status().isOk()).andReturn());
        String account=profile.get("account").asText();
        JsonNode second=body(http.perform(post("/api/v1/auth/login").contentType("application/json")
            .content(json.writeValueAsBytes(Map.of("account",account,"password","Other-test-password-928!")))).andExpect(status().isOk()).andReturn());
        http.perform(post("/api/v1/users/me/password").header("Authorization",bearer(first)).header("Idempotency-Key",UUID.randomUUID())
            .contentType("application/json").content("{\"currentPassword\":\"Other-test-password-928!\",\"newPassword\":\"Changed-test-password-839!\"}"))
            .andExpect(status().isNoContent());
        http.perform(get("/api/v1/auth/me").header("Authorization",bearer(second.get("accessToken").asText()))).andExpect(status().isUnauthorized());
        http.perform(patch("/api/v1/users/me").header("Authorization",bearer(first)).header("If-Match","\"1\"")
            .contentType("application/merge-patch+json").content("{\"displayName\":\"更新姓名\"}"))
            .andExpect(status().isOk()).andExpect(header().string("ETag","\"2\""));
        http.perform(get("/api/v1/auth/me").header("Authorization",bearer(first))).andExpect(status().isOk())
            .andExpect(header().string("ETag","\"2\"")).andExpect(jsonPath("$.displayName").value("更新姓名"));
        http.perform(post("/api/v1/auth/login").contentType("application/json")
            .content(json.writeValueAsBytes(Map.of("account",account,"password","Other-test-password-928!")))).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/auth/login").contentType("application/json")
            .content(json.writeValueAsBytes(Map.of("account",account,"password","Changed-test-password-839!")))).andExpect(status().isOk());
    }

    /** 发送创建请求并检查201响应，返回新资源。 */
    JsonNode create(String token,String path,Object value) throws Exception {
        var req=post("/api/v1"+path).header("Authorization",bearer(token)).header("Idempotency-Key",UUID.randomUUID());
        if(value!=null) req.contentType("application/json").content(json.writeValueAsBytes(value));
        return body(http.perform(req).andExpect(status().isCreated()).andReturn());
    }
    /** 获取默认测试账号的访问令牌。 */
    String token() throws Exception { return login().get("accessToken").asText(); }
    /** 为测试令牌添加Bearer前缀。 */
    String bearer(String token) { return "Bearer "+token; }
    /** 创建或登录另一测试账号，供账号隔离断言使用。 */
    String otherToken() throws Exception {
        String account="other-"+UUID.randomUUID();
        db.update("INSERT INTO users(id,account,normalized_account,display_name,password_hash,status,revision,generation,sync_position,created_at,updated_at) VALUES(?,?,?,?,?,'ACTIVE',1,1,0,1,1)",
            UUID.randomUUID().toString(),account,account,"other",new BCryptPasswordEncoder(10).encode("Other-test-password-928!"));
        return body(http.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsBytes(Map.of("account",account,"password","Other-test-password-928!"))))
            .andExpect(status().isOk()).andReturn()).get("accessToken").asText();
    }

    /** 登录默认测试账号并返回完整令牌响应。 */
    JsonNode login() throws Exception {
        return body(http.perform(post("/api/v1/auth/login").contentType("application/json")
            .content("{\"account\":\"test-owner\",\"password\":\"Owner-test-password-928!\",\"requestedRole\":\"HOSPITAL_STAFF\"}"))
            .andExpect(status().isOk()).andReturn());
    }
    /** 解析测试HTTP响应的JSON正文。 */
    JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsByteArray()); }
}
