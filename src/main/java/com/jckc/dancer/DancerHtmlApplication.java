package com.jckc.dancer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@MapperScan("com.jckc.dancer.modules.*.mapper")
@SpringBootConfiguration
@EnableTransactionManagement
@EnableScheduling
public class DancerHtmlApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(DancerHtmlApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(DancerHtmlApplication.class);
    }

}
