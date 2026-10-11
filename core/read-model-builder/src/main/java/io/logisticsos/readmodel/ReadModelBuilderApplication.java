package io.logisticsos.readmodel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReadModelBuilderApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReadModelBuilderApplication.class, args);
    }
}