package io.logisticsos.entitlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EntitlementLoaderTest {

    private static final String VALID = """
            {
              "licenseId": "LIC-2026-0001",
              "tenant": "acme",
              "edition": "enterprise",
              "version": "1.0.0",
              "issuedAt": "2026-01-01T00:00:00Z",
              "expiresAt": "2027-01-01T00:00:00Z",
              "features": { "l5_orchestration": true, "sso_saml": false },
              "signature": "sig"
            }
            """;

    @Test
    void blankLicenseIsPermissiveEvaluation() {
        assertThat(EntitlementLoader.load(null)).isEqualTo(Entitlement.evaluation());
        assertThat(EntitlementLoader.load("  ")).isEqualTo(Entitlement.evaluation());
    }

    @Test
    void parsesInlineLicenseJson() {
        Entitlement e = EntitlementLoader.load(VALID);

        assertThat(e.mode()).isEqualTo(Entitlement.Mode.LICENSED);
        assertThat(e.edition()).isEqualTo("enterprise");
        assertThat(e.version()).isEqualTo("1.0.0");
        assertThat(e.tenant()).isEqualTo("acme");
        assertThat(e.isEnabled("l5_orchestration")).isTrue();
        assertThat(e.isEnabled("sso_saml")).isFalse();
        assertThat(e.expiresAt()).isNotNull();
    }

    @Test
    void ignoresUnknownFields() {
        String withExtra = """
                { "edition": "standard", "version": "1.0.0", "limits": { "seats": 10 } }
                """;

        assertThat(EntitlementLoader.parse(withExtra).edition()).isEqualTo("standard");
    }

    @Test
    void readsLicenseFromFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("license.json");
        Files.writeString(file, VALID);

        assertThat(EntitlementLoader.load(file.toString()).edition()).isEqualTo("enterprise");
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> EntitlementLoader.parse("{ not-json"))
                .isInstanceOf(EntitlementException.class);
    }

    @Test
    void rejectsLicenseWithoutEdition() {
        assertThatThrownBy(() -> EntitlementLoader.parse("{ \"version\": \"1.0.0\" }"))
                .isInstanceOf(EntitlementException.class)
                .hasMessageContaining("edition");
    }
}