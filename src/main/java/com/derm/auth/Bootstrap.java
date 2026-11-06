package com.derm.auth;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.context.ConfigurableApplicationContext;

@Component
public class Bootstrap implements ApplicationRunner {
    private final AuthService auth;
    private final String account,password;
    private final ConfigurableApplicationContext context;
    private final String provisionAccount,provisionPassword,provisionName,provisionRole;
    /** 初始化本组件所需的依赖与配置。 */
    public Bootstrap(AuthService auth,@Value("${app.bootstrap-account}")String account,@Value("${app.bootstrap-password}")String password,
        ConfigurableApplicationContext context,@Value("${app.provision-account:}")String provisionAccount,
        @Value("${app.provision-password:}")String provisionPassword,@Value("${app.provision-name:}")String provisionName,@Value("${app.provision-role:PATIENT}")String provisionRole) {
        this.auth=auth;this.account=account;this.password=password;this.context=context;
        this.provisionAccount=provisionAccount;this.provisionPassword=provisionPassword;this.provisionName=provisionName;this.provisionRole=provisionRole;
    }
    /** 启动时预置患者账号，或在非Web维护模式下创建账号并退出。 */
    @Override public void run(ApplicationArguments args) {
        if(!provisionAccount.isBlank()) {
            if(context.getEnvironment().getProperty("spring.main.web-application-type","").equalsIgnoreCase("none")) {
                auth.createAccount(provisionAccount,provisionPassword,provisionName,provisionRole);
                System.out.println("Account created successfully; existing accounts were not modified.");context.close();
            } else throw new IllegalStateException("Account maintenance requires non-web mode");
        } else if(!account.isBlank()) auth.provision(account,password);
    }
}
