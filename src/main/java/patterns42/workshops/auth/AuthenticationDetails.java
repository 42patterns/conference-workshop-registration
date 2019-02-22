package patterns42.workshops.auth;

import lombok.ToString;

import java.util.Optional;

@ToString
public class AuthenticationDetails {
    public final String username;
    public final String password;

    public AuthenticationDetails(Optional<String> maybeUsername, Optional<String> maybePassword) {
        this.username = maybeUsername
                .orElse("admin");
        this.password = maybePassword
                .orElseGet(() -> generatePassword());
    }

    private String generatePassword() {
        return null; //TODO: generate random password
    }

}
