package io.logisticsos.bffops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BffOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(BffOpsApplication.class, args);
    }
}