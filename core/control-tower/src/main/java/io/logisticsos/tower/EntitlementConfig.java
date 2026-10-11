package io.logisticsos.tower;

import io.logisticsos.entitlement.EntitlementGate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the edition entitlement as a bean. Soft phase: resolves permissively from
 * {@code ENTITLEMENT_LICENSE} (see docs/product/licensing.md).
 */
@Configuration(proxyBeanMethods = false)
public class EntitlementConfig {

    @Bean
    public EntitlementGate entitlementGate() {
        return EntitlementGate.fromEnvironment();
    }
}