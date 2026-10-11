package io.logisticsos.security;

import java.util.List;

/** A dev/offline user from {@code users.json}: credentials, tenant and roles. */
public record LocalUser(String username, String password, String tenant, List<String> roles) {

    public LocalUser {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
