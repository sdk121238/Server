package com.derm.auth;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Bootstrap implements ApplicationRunner {
    private final AuthService auth;
    private final String account,password;
    public Bootstrap(AuthService auth,@Value("${app.bootstrap-account}")String account,@Value("${app.bootstrap-password}")String password) { this.auth=auth;this.account=account;this.password=password; }
    @Override public void run(ApplicationArguments args) { if(!account.isBlank()) auth.provision(account,password); }
}
