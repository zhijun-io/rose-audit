package io.zhijun.audit.sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

/** Entry point for the runnable web and JDBC audit demonstration. */
@SpringBootApplication
@EnableSpringDataWebSupport
public class AuditSampleApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuditSampleApplication.class, args);
    }
}
