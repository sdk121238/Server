package com.derm;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.ActiveProfiles;
@ActiveProfiles({"test","mysql-test"})
@EnabledIfEnvironmentVariable(named="RUN_MYSQL_TESTS",matches="true")
class MySqlPatientDoctorTest extends PatientDoctorIntegrationTest {}
