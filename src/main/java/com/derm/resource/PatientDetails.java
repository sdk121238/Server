package com.derm.resource;

import com.derm.api.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.time.*;
import java.time.temporal.ChronoUnit;

@Component
public class PatientDetails {
    private final JdbcTemplate db;
    private final Json json;
    private final Clock clock;
    /** 初始化本组件所需的依赖与配置。 */
    @org.springframework.beans.factory.annotation.Autowired
    public PatientDetails(JdbcTemplate db,Json json) { this(db,json,Clock.system(ZoneId.of("Asia/Shanghai"))); }
    /** 初始化本组件所需的依赖与配置。 */
    public PatientDetails(JdbcTemplate db,Json json,Clock clock) { this.db=db;this.json=json;this.clock=clock; }

    /** 补齐可空资料并校验电话、出生信息和有效主治医师，协调新旧生日字段。 */
    public void normalize(ObjectNode data,JsonNode patch) {
        for(String field:new String[]{"phoneNumber","gender","birthYearMonth","attendingDoctorId","birthDate"}) if(!data.has(field)) data.putNull(field);
        if(data.hasNonNull("phoneNumber")) {
            String phone=data.get("phoneNumber").asText().trim();
            if(phone.isEmpty()) data.putNull("phoneNumber");
            else {if(!phone.matches("\\+?[0-9]{7,15}")) throw ApiException.invalid("电话号码须为可选+号和7至15位数字");data.put("phoneNumber",phone);}
        }
        LocalDate today=LocalDate.now(clock);YearMonth month=null;LocalDate birthday=null;
        try {
            if(data.hasNonNull("birthDate")) {birthday=LocalDate.parse(data.get("birthDate").asText());if(birthday.isAfter(today)) throw ApiException.invalid("出生日期不能在未来");}
            if(data.hasNonNull("birthYearMonth")) {
                String raw=data.get("birthYearMonth").asText();if(!raw.matches("[0-9]{4}-[0-9]{2}")) throw ApiException.invalid("出生年月格式应为YYYY-MM");
                month=YearMonth.parse(raw);if(month.isAfter(YearMonth.from(today))) throw ApiException.invalid("出生年月不能在未来");
            }
        } catch(java.time.DateTimeException e) {throw ApiException.invalid("出生日期或年月无效");}
        if(birthday!=null) {
            YearMonth actual=YearMonth.from(birthday);
            if(patch.has("birthDate")&&!patch.has("birthYearMonth")) {month=actual;data.put("birthYearMonth",actual.toString());}
            else if(month==null) {
                if(patch.has("birthYearMonth")) throw ApiException.invalid("清空出生年月时请同时清空出生日期");
                data.put("birthYearMonth",actual.toString());
            } else if(!month.equals(actual)) {
                if(patch.has("birthYearMonth")&&!patch.has("birthDate")) data.putNull("birthDate");
                else throw ApiException.invalid("出生年月与出生日期不一致");
            }
        }
        if(patch.hasNonNull("attendingDoctorId")) {
            Integer count=db.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND role='HOSPITAL_STAFF' AND status='ACTIVE'",Integer.class,data.get("attendingDoctorId").asText());
            if(count==null||count!=1) throw ApiException.invalid("主治医师必须是有效的已登记医生账号");
        }
    }

    /** 生成读取视图，动态计算年龄并附加主治医师摘要，不改存储版本。 */
    public ObjectNode present(ObjectNode stored) {
        ObjectNode out=stored.deepCopy();
        for(String field:new String[]{"phoneNumber","gender","birthYearMonth","attendingDoctorId"}) if(!out.has(field)) out.putNull(field);
        if(!out.hasNonNull("birthYearMonth")&&out.hasNonNull("birthDate")) out.put("birthYearMonth",out.get("birthDate").asText().substring(0,7));
        Integer age=age(out,LocalDate.now(clock));if(age==null) out.putNull("age");else out.put("age",age);
        out.putNull("attendingDoctor");
        if(out.hasNonNull("attendingDoctorId")) {
            String id=out.get("attendingDoctorId").asText();var rows=db.queryForList("SELECT display_name,role,status FROM users WHERE id=?",id);
            ObjectNode doctor=json.object().put("id",id).put("unavailable",rows.isEmpty()||!"HOSPITAL_STAFF".equals(rows.getFirst().get("role"))||!"ACTIVE".equals(rows.getFirst().get("status")));
            if(rows.isEmpty()) doctor.putNull("displayName");else doctor.put("displayName",rows.getFirst().get("display_name").toString());out.set("attendingDoctor",doctor);
        }
        return out;
    }
    /** 完整生日按周岁、仅出生年月按整月计算年龄；缺少信息返回null。 */
    public static Integer age(JsonNode patient,LocalDate today) {
        if(patient.hasNonNull("birthDate")) return Period.between(LocalDate.parse(patient.get("birthDate").asText()),today).getYears();
        if(patient.hasNonNull("birthYearMonth")) return (int)(ChronoUnit.MONTHS.between(YearMonth.parse(patient.get("birthYearMonth").asText()),YearMonth.from(today))/12);
        return null;
    }
}
