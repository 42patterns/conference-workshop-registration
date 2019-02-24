package patterns42.workshops.auth;

import io.javalin.BasicAuthCredentials;
import io.javalin.UnauthorizedResponse;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Optional;

public class AdminAuthenticationDetailsTest {

    private final String USERNAME = "admin";
    private final String PASSWORD = "password";
    private AdminAuthenticationDetails auth;

    @Before
    public void setup() {
        this.auth = new AdminAuthenticationDetails(
                Optional.of(USERNAME), Optional.of(PASSWORD)
        );
    }

    @Test
    public void should_auth_with_admin_password() {
        BasicAuthCredentials credentials = new BasicAuthCredentials(USERNAME, PASSWORD);
        boolean result = auth.authorize(credentials);

        Assert.assertThat(result, CoreMatchers.is(CoreMatchers.equalTo(true)));
    }

    @Test
    public void should_throw_not_authorized_when_no_basic_auth_passed() {
        try {
            auth.authorize(null);
            Assert.fail("UnauthorizedResponse not thrown");
        } catch (UnauthorizedResponse e) {
            Assert.assertThat(e.getMessage(), CoreMatchers.equalTo("Unauthorized"));
        }
    }
}