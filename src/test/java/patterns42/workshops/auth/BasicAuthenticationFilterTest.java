package patterns42.workshops.auth;

import org.junit.Test;
import spark.HaltException;
import spark.Request;
import spark.Response;

import java.util.Base64;
import java.util.Optional;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BasicAuthenticationFilterTest {
    private static final String CORRECT_PASSWORD = "password";

    private static final String CORRECT_USERNAME = "username";

    private static final String MISSING = "";

    private static final String INCORRECT = "incorrect";

    private static final String INCORRECT_PASSWORD = generateBasicAuthenticationHeader(CORRECT_USERNAME, INCORRECT);

    private static final String CORRECT = generateBasicAuthenticationHeader(CORRECT_USERNAME, CORRECT_PASSWORD);

    private static final String MISSING_PASSWORD = generateBasicAuthenticationHeader(CORRECT_USERNAME, MISSING);

    private static final String MISSING_USERNAME = generateBasicAuthenticationHeader(MISSING, CORRECT_PASSWORD);

    private static final String INCORRECT_USERNAME = generateBasicAuthenticationHeader(INCORRECT, CORRECT_PASSWORD);

    private static final String MISSING_AUTHORISATION_DETAILS = generateBasicAuthenticationHeader(MISSING, MISSING);

    private final Request request = mock(Request.class);

    private final Response response = mock(Response.class);

    private final BasicAuthenticationFilter basicAuthenticationFilter =
            new BasicAuthenticationFilter(new AuthenticationDetails(Optional.of(CORRECT_USERNAME), Optional.of(CORRECT_PASSWORD)));

    private static String generateBasicAuthenticationHeader(final String username, final String password) {
        return String.format("Basic %s", new String(Base64.getEncoder().encode(String.format("%s:%s", username, password).getBytes())));
    }

    @Test(expected = HaltException.class)
    public void shouldReturn401WhenMissingAuthenticationHeader() {
        whenAuthorisationHeader(MISSING);

        expect401();
    }

    @Test(expected = HaltException.class)
    public void shouldReturn401WhenMissingAuthenticationDetails() {
        whenAuthorisationHeader(MISSING_AUTHORISATION_DETAILS);

        expect401();
    }

    @Test(expected = HaltException.class)
    public void shouldReturn401WhenPasswordMissing() {
        whenAuthorisationHeader(MISSING_PASSWORD);

        expect401();
    }

    @Test(expected = HaltException.class)
    public void shouldReturn401WhenUsernameMissing() {
        whenAuthorisationHeader(MISSING_USERNAME);

        expect401();
    }

    @Test(expected = HaltException.class)
    public void shouldReturn401WhenUsernameIncorrect() {
        whenAuthorisationHeader(INCORRECT_USERNAME);

        expect401();
    }

    @Test(expected = HaltException.class)
    public void shouldReturn401WhenPasswordIncorrect() {
        whenAuthorisationHeader(INCORRECT_PASSWORD);

        expect401();
    }

    @Test
    public void shouldReturn200ForCorrectAuthorizationDetails() {
        whenAuthorisationHeader(CORRECT);

        basicAuthenticationFilter.handle(request, response);
    }

    private void whenAuthorisationHeader(final String headerValue) {
        when(request.headers("Authorization")).thenReturn(headerValue);
    }

    private void expect401() {
        try {
            basicAuthenticationFilter.handle(request, response);
        } catch (HaltException e) {
            assertThat(e.statusCode(), equalTo(401));
            throw e;
        }
    }
}
