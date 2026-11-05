package com.derm;

import com.derm.auth.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.time.*;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.security.MessageDigest;
import java.net.URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientDoctorIntegrationTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;
    @Autowired AuthService auth;
    final String password="Tests-only-password-843!";
    record User(String id,String token) {}
    User user(boolean doctor) throws Exception {
        String account="role-"+UUID.randomUUID(); auth.provision(account,password);
        String id=db.queryForObject("SELECT id FROM users WHERE account=?",String.class,account);
        if(doctor) db.update("UPDATE users SET role='HOSPITAL_STAFF' WHERE id=?",id);
        JsonNode login=body(http.perform(post("/api/v1/auth/login").contentType("application/json")
            .content(json.writeValueAsBytes(Map.of("account",account,"password",password,"requestedRole","HOSPITAL_STAFF"))))
            .andExpect(status().isOk()).andReturn());
        assertThat(login.at("/profile/role").asText()).isEqualTo(doctor?"HOSPITAL_STAFF":"PATIENT");
        return new User(id,login.get("accessToken").asText());
    }
    JsonNode body(MvcResult r) throws Exception { return json.readTree(r.getResponse().getContentAsByteArray()); }
    MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder r,User user) { return r.header("Authorization","Bearer "+user.token()); }
    JsonNode create(User u,String path,Object value) throws Exception {
        return body(http.perform(as(post("/api/v1"+path),u).header("Idempotency-Key",UUID.randomUUID().toString())
            .contentType("application/json").content(json.writeValueAsBytes(value))).andExpect(status().isCreated()).andReturn());
    }
    JsonNode patient(User u,String name) throws Exception {return create(u,"/patients",Map.of("id",UUID.randomUUID(),"name",name));}

    @Test void patientDemographicsArePersistedAndAgeIsDerived() throws Exception {
        User u=user(false);String month=YearMonth.now(ZoneId.of("Asia/Shanghai")).minusYears(25).toString();
        JsonNode p=create(u,"/patients",Map.of("id",UUID.randomUUID(),"name","资料测试","phoneNumber","+8613800000000","gender","FEMALE","birthYearMonth",month));
        assertThat(p.get("age").asInt()).isEqualTo(25); assertThat(p.get("attendingDoctorId").isNull()).isTrue();
        assertThat(db.queryForObject("SELECT phone_number FROM patients WHERE id=?",String.class,p.get("id").asText())).isEqualTo("+8613800000000");
        String path="/api/v1/patients/"+p.get("id").asText();
        http.perform(as(patch(path),u).header("If-Match","\"1\"").contentType("application/merge-patch+json")
            .content("{\"phoneNumber\":null,\"gender\":\"UNKNOWN\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.phoneNumber").isEmpty()).andExpect(jsonPath("$.age").value(25));
        assertThat(db.queryForObject("SELECT phone_number FROM patients WHERE id=?",String.class,p.get("id").asText())).isNull();
    }

    @Test void demographicsRejectInvalidValuesAndReadOnlyAge() throws Exception {
        User u=user(false);
        for(Map<String,Object> extra:List.<Map<String,Object>>of(Map.of("phoneNumber","abc"),Map.of("gender","BAD"),Map.of("birthYearMonth","2020-13"),Map.of("birthYearMonth","2999-01"),Map.of("birthDate","2999-01-01"),Map.of("age",22),Map.of("birthDate","2000-03-05","birthYearMonth","2000-04"))) {
            Map<String,Object> input=new HashMap<>(extra);input.put("id",UUID.randomUUID());input.put("name","非法资料");
            http.perform(as(post("/api/v1/patients"),u).header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json")
                .content(json.writeValueAsBytes(input))).andExpect(status().isUnprocessableEntity());
        }
    }

    @Test void birthDateCompatibilityAndMonthChangesDoNotLeaveContradictions() throws Exception {
        User u=user(false);JsonNode p=create(u,"/patients",Map.of("id",UUID.randomUUID(),"name","生日","birthDate","2000-03-05"));
        assertThat(p.path("birthYearMonth").asText()).isEqualTo("2000-03");
        http.perform(as(patch("/api/v1/patients/"+p.get("id").asText()),u).header("If-Match","\"1\"")
            .contentType("application/merge-patch+json").content("{\"birthYearMonth\":\"2001-04\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.birthDate").isEmpty()).andExpect(jsonPath("$.birthYearMonth").value("2001-04"));
    }

    @Test void doctorsReadAllPatientsButCannotWriteOtherAccountsAndMineIsAssignmentBased() throws Exception {
        User a=user(false),b=user(false),d=user(true);String name="scope-"+UUID.randomUUID();
        JsonNode pa=create(a,"/patients",Map.of("id",UUID.randomUUID(),"name",name+" A","attendingDoctorId",d.id()));
        JsonNode pb=patient(b,name+" B");
        http.perform(as(get("/api/v1/patients/"+pa.get("id").asText()),b)).andExpect(status().isNotFound());
        http.perform(as(get("/api/v1/patients/"+pb.get("id").asText()),d)).andExpect(status().isOk());
        JsonNode all=body(http.perform(as(get("/api/v1/patients").param("q",name).param("limit","1"),d)).andExpect(status().isOk()).andReturn());
        assertThat(all.path("items").size()).isEqualTo(1); assertThat(all.get("nextPageToken").isTextual()).isTrue();
        String page=all.get("nextPageToken").asText();
        JsonNode second=body(http.perform(as(get("/api/v1/patients").param("q",name).param("limit","1").param("pageToken",page),d)).andExpect(status().isOk()).andReturn());
        assertThat(second.at("/items/0/id").asText()).isNotEqualTo(all.at("/items/0/id").asText());
        http.perform(as(get("/api/v1/patients").param("q",name).param("scope","mine").param("pageToken",page),d)).andExpect(status().isGone());
        JsonNode mine=body(http.perform(as(get("/api/v1/patients").param("q",name).param("scope","mine"),d)).andExpect(status().isOk()).andReturn());
        assertThat(mine.path("items").size()).isEqualTo(1); assertThat(mine.at("/items/0/id").asText()).isEqualTo(pa.path("id").asText());
        JsonNode own=body(http.perform(as(get("/api/v1/patients").param("q",name).param("scope","all"),a)).andExpect(status().isOk()).andReturn());
        assertThat(own.path("items").size()).isEqualTo(1);
        http.perform(as(patch("/api/v1/patients/"+pb.path("id").asText()),d).header("If-Match","\"1\"").contentType("application/merge-patch+json").content("{\"name\":\"越权\"}" )).andExpect(status().isNotFound());
        http.perform(as(delete("/api/v1/patients/"+pb.path("id").asText()),d).header("If-Match","\"1\"")).andExpect(status().isNotFound());
        http.perform(as(get("/api/v1/patients").param("scope","invalid"),d)).andExpect(status().isUnprocessableEntity());
        db.update("UPDATE users SET role='PATIENT' WHERE id=?",d.id());
        http.perform(as(get("/api/v1/patients/"+pb.path("id").asText()),d)).andExpect(status().isNotFound());
    }

    @Test void doctorDirectoryAndAssignmentValidateRegisteredActiveDoctor() throws Exception {
        User a=user(false),d=user(true),disabled=user(true);db.update("UPDATE users SET status='DISABLED' WHERE id=?",disabled.id());
        JsonNode directory=body(http.perform(as(get("/api/v1/doctors"),a)).andExpect(status().isOk()).andReturn());
        assertThat(directory.toString()).contains(d.id()).doesNotContain(disabled.id(),"password","accessToken","account");
        for(String id:List.of(a.id(),disabled.id(),UUID.randomUUID().toString())) {
            http.perform(as(post("/api/v1/patients"),a).header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json")
                .content(json.writeValueAsBytes(Map.of("id",UUID.randomUUID(),"name","关联验证","attendingDoctorId",id))))
                .andExpect(status().isUnprocessableEntity());
        }
        JsonNode p=create(a,"/patients",Map.of("id",UUID.randomUUID(),"name","关联医生","attendingDoctorId",d.id()));
        assertThat(p.at("/attendingDoctor/id").asText()).isEqualTo(d.id());
        db.update("UPDATE users SET status='DISABLED' WHERE id=?",d.id());
        http.perform(as(get("/api/v1/patients/"+p.path("id").asText()),a)).andExpect(status().isOk()).andExpect(jsonPath("$.attendingDoctor.unavailable").value(true));
        http.perform(as(patch("/api/v1/patients/"+p.path("id").asText()),a).header("If-Match","\"1\"").contentType("application/merge-patch+json").content("{\"attendingDoctorId\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.attendingDoctorId").isEmpty());
    }

    @Test void doctorPersonalSnapshotDoesNotDownloadOtherOwnersData() throws Exception {
        User a=user(false),d=user(true);patient(a,"他人的离线数据");
        JsonNode snapshot=create(d,"/sync/snapshots",Map.of());
        http.perform(as(get("/api/v1/sync/snapshots/"+snapshot.path("id").asText()),d)).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test void doctorCannotReadUnverifiedOrAbortedFileMetadataAcrossOwners() throws Exception {
        User a=user(false),d=user(true);String patient=patient(a,"未完成图片").path("id").asText();
        JsonNode upload=create(a,"/file-uploads",Map.of("patientId",patient,"strategy","SINGLE","mimeType","image/png","width",20,"height",20,"sizeBytes",83,"sha256","0aedc9bece813993a4241852d4826e275f95d7cd3877a25e052922f2b84e3c0c"));
        String path="/api/v1/files/"+upload.path("fileId").asText();
        http.perform(as(get(path),a)).andExpect(status().isOk());
        http.perform(as(get(path),d)).andExpect(status().isNotFound());
        http.perform(as(delete("/api/v1/file-uploads/"+upload.path("id").asText()),a)).andExpect(status().isNoContent());
        http.perform(as(get(path),d)).andExpect(status().isNotFound());
    }

    @Test void doctorsReadChildResourcesAndPicturesButSignedTicketsRespectCurrentRoleAndParent() throws Exception {
        User a=user(false),b=user(false),d=user(true);
        JsonNode p=patient(a,"图片权限");String patient=p.path("id").asText();
        JsonNode l=create(a,"/lesions",Map.of("id",UUID.randomUUID(),"patientId",patient,"bodySite","手"));
        ByteArrayOutputStream output=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB),"png",output);byte[] png=output.toByteArray();
        JsonNode upload=create(a,"/file-uploads",Map.of("patientId",patient,"strategy","SINGLE","mimeType","image/png","width",20,"height",20,"sizeBytes",png.length,"sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png))));
        URI putUrl=URI.create(upload.path("uploadUrl").asText());
        http.perform(put(putUrl.getPath()+"?"+putUrl.getRawQuery()).contentType("image/png").content(png)).andExpect(status().isNoContent());
        http.perform(as(post("/api/v1/file-uploads/"+upload.path("id").asText()+"/complete"),a).header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json").content("{}")).andExpect(status().isOk());
        String file=upload.path("fileId").asText();
        JsonNode c=create(a,"/captures",Map.of("id",UUID.randomUUID(),"lesionId",l.path("id").asText(),"imageFileId",file,"capturedAt",1,"deviceModel","test-camera","source","GALLERY","assetKind","CAPTURE_IMAGE","referenceOnly",false));
        for(String path:List.of("/patients/"+patient+"/lesions","/lesions/"+l.path("id").asText(),"/lesions/"+l.path("id").asText()+"/captures","/captures/"+c.path("id").asText(),"/files/"+file)) {
            http.perform(as(get("/api/v1"+path),d)).andExpect(status().isOk());
            http.perform(as(get("/api/v1"+path),b)).andExpect(status().isNotFound());
        }
        http.perform(as(post("/api/v1/lesions"),d).header("Idempotency-Key",UUID.randomUUID().toString()).contentType("application/json")
            .content(json.writeValueAsBytes(Map.of("id",UUID.randomUUID(),"patientId",patient,"bodySite","越权")))).andExpect(status().isNotFound());
        JsonNode ticket=body(http.perform(as(post("/api/v1/files/"+file+"/download-url"),d).header("Idempotency-Key",UUID.randomUUID().toString())).andExpect(status().isOk()).andReturn());
        URI download=URI.create(ticket.path("url").asText());String url=download.getPath()+"?"+download.getRawQuery();
        http.perform(get(url)).andExpect(status().isOk()).andExpect(content().bytes(png));
        db.update("UPDATE users SET status='DISABLED' WHERE id=?",d.id());http.perform(get(url)).andExpect(status().isNotFound());
        db.update("UPDATE users SET status='ACTIVE' WHERE id=?",d.id());
        db.update("UPDATE users SET role='PATIENT' WHERE id=?",d.id());http.perform(get(url)).andExpect(status().isNotFound());
        db.update("UPDATE users SET role='HOSPITAL_STAFF' WHERE id=?",d.id());
        http.perform(as(delete("/api/v1/patients/"+patient),a).header("If-Match","\"1\"")).andExpect(status().isNoContent());
        http.perform(get(url)).andExpect(status().isNotFound());
    }
}
