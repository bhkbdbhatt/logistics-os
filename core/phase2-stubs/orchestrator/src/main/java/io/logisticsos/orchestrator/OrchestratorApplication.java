package io.logisticsos.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Phase-2 command stub (L5). Proves the orchestrator surface boots and emits valid
 * {@code orch.command.v1} commands without yet implementing the real orchestration engine
 * (workflow engine, sagas, decision routing are 0.3.0+ scoped).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class OrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}