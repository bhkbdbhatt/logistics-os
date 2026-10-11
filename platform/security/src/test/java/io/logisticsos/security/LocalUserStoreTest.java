package io.logisticsos.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

class LocalUserStoreTest {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Test
    void authenticatesValidDevUser() {
        LocalUserStore store = new LocalUserStore(LocalUserStore.devUsers(), encoder);

        LocalUser user = store.authenticate("ops", "ops");

        assertThat(user.username()).isEqualTo("ops");
        assertThat(user.tenant()).isEqualTo("acme");
        assertThat(user.roles()).contains("operator");
    }

    @Test
    void rejectsWrongPassword() {
        LocalUserStore store = new LocalUserStore(LocalUserStore.devUsers(), encoder);

        assertThatThrownBy(() -> store.authenticate("admin", "nope"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsUnknownUser() {
        LocalUserStore store = new LocalUserStore(LocalUserStore.devUsers(), encoder);

        assertThatThrownBy(() -> store.authenticate("ghost", "x"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loadsUsersFromJson() throws Exception {
        Path file = Path.of(getClass().getResource("/users.json").toURI());

        LocalUserStore store = new LocalUserStore(LocalUserStore.load(file), encoder);

        assertThat(store.authenticate("alice", "secret").tenant()).isEqualTo("acme");
        assertThat(store.authenticate("bob", "hunter2").tenant()).isEqualTo("globex");
    }

    @Test
    void parsesMissingUsersArrayAsError() throws Exception {
        var root = new ObjectMapper().readTree("{\"nope\":[]}");

        assertThatThrownBy(() -> LocalUserStore.parse(root)).isInstanceOf(IllegalStateException.class);
    }
}
