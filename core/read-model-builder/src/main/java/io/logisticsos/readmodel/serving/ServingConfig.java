package io.logisticsos.readmodel.serving;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ServingConfig {

    @Bean
    public ReadModelCache readModelCache() {
        return new ReadModelCache();
    }
}