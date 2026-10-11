package io.logisticsos.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IngestGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(IngestGatewayApplication.class, args);
    }
}