package com.derm;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;

class PatientMigrationTest {
    /** 验证旧患者迁移回填且版本及归属不变。 */
    @Test void legacyPatientBirthdayIsBackfilledWithoutChangingRevisionOrOwnership() {
        String url="jdbc:h2:mem:migration-"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").target("1").load().migrate();
        JdbcTemplate db=new JdbcTemplate(new DriverManagerDataSource(url,"sa",""));
        String user=UUID.randomUUID().toString(),patient=UUID.randomUUID().toString();
        db.update("INSERT INTO users VALUES(?,?,?,?,?,'ACTIVE',1,1,0,1,1)",user,"legacy","legacy","旧账号","hash");
        db.update("INSERT INTO patients(id,owner_id,revision,payload) VALUES(?,?,7,?)",patient,user,"{\"id\":\""+patient+"\",\"name\":\"旧患者\",\"birthDate\":\"1990-04-05\",\"revision\":7,\"createdAt\":1,\"updatedAt\":2}");
        Flyway.configure().dataSource(url,"sa","").load().migrate();
        assertThat(db.queryForObject("SELECT role FROM users WHERE id=?",String.class,user)).isEqualTo("PATIENT");
        var row=db.queryForMap("SELECT * FROM patients WHERE id=?",patient);
        assertThat(row.get("birth_year_month")).isEqualTo("1990-04");
        assertThat(row.get("patient_name")).isEqualTo("旧患者");
        assertThat(((Number)row.get("revision")).intValue()).isEqualTo(7);
        assertThat(row.get("owner_id")).isEqualTo(user);
        assertThat(row.get("payload").toString()).contains("\"birthYearMonth\":\"1990-04\"","\"updatedAt\":2");
    }
}
