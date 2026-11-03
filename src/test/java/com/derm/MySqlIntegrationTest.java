package com.derm;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.ActiveProfiles;

/** Runs the same HTTP and transaction assertions against the existing MySQL 8.0 binary. */
@ActiveProfiles({"test","mysql-test"})
@EnabledIfEnvironmentVariable(named="RUN_MYSQL_TESTS",matches="true")
class MySqlIntegrationTest extends BackendIntegrationTest {}
