package patterns42.workshops.auth;

import java.util.Objects;
import java.util.Optional;

public class AuthenticationDetails {
    public final String username;
    public final String password;

    public AuthenticationDetails(Optional<String> maybeUsername, Optional<String> maybePassword) {
        this.username = maybeUsername
                .orElseThrow(() -> new RuntimeException("Empty username"));
        this.password = maybePassword
                .orElseThrow(() -> new RuntimeException("Empty password"));
    }

    public AuthenticationDetails(String[] credentials) {
        Objects.requireNonNull(credentials, "Credentials cannot be null");
        this.username = credentials[0];
        this.password = credentials[1];
    }
}
