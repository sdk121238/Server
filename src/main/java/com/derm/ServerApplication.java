package com.derm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ServerApplication {
    /** 启动Spring Boot应用，加载配置、数据库迁移和业务组件。 */
    public static void main(String[] args) { SpringApplication.run(ServerApplication.class, args); }
}
