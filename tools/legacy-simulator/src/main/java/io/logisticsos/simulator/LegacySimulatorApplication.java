package io.logisticsos.simulator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class LegacySimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegacySimulatorApplication.class, args);
    }
}