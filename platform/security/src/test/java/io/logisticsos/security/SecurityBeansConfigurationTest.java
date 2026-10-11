package io.logisticsos.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class SecurityBeansConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityBeansConfiguration.class));

    @Test
    void localModeRegistersIssuerValidatorAndDevAuth() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PasswordEncoder.class);
            assertThat(context).hasSingleBean(JwtDecoder.class);
            assertThat(context).hasSingleBean(JwtEncoder.class);
            assertThat(context).hasSingleBean(LocalUserStore.class);
            assertThat(context).hasSingleBean(LocalTokenService.class);
            assertThat(context).hasSingleBean(AuthController.class);
            assertThat(context).hasSingleBean(TenantContextFilter.class);
        });
    }

    @Test
    void localModeHasNoIssuerBeansInOidcMode() {
        runner.withPropertyValues("logistics.security.mode=oidc", "logistics.security.issuer-uri=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shortSecretFailsFast() {
        runner.withPropertyValues("logistics.security.local.secret=too-short")
                .run(context -> assertThat(context).hasFailed());
    }
}
