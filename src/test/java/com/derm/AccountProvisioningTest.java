package com.derm;
import com.derm.auth.AuthService;
import com.derm.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AccountProvisioningTest {
    @Autowired AuthService auth;
    @Autowired JdbcTemplate db;
    @Test void localDoctorCreationStoresRoleAndCannotPromoteOrOverwriteExistingAccount() {
        String account="doctor-"+UUID.randomUUID();
        String id=auth.createAccount(account,"Doctor-test-password-883!","测试医生","HOSPITAL_STAFF");
        assertThat(db.queryForMap("SELECT role,display_name FROM users WHERE id=?",id)).containsEntry("role","HOSPITAL_STAFF").containsEntry("display_name","测试医生");
        String hash=db.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,id);
        assertThat(hash).isNotEqualTo("Doctor-test-password-883!");
        assertThatThrownBy(()->auth.createAccount(account.toUpperCase(),"Different-password-939!","覆盖","PATIENT")).isInstanceOf(ApiException.class);
        assertThat(db.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,id)).isEqualTo(hash);
        assertThatThrownBy(()->auth.createAccount("invalid-"+UUID.randomUUID(),"Doctor-test-password-883!","测试","ADMIN")).isInstanceOf(ApiException.class);
    }
}
