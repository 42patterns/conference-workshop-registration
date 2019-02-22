//package patterns42.workshops.auth;
//
//import java.util.Base64;
//import java.util.Optional;
//import java.util.function.Supplier;
//
//
//public class BasicAuthenticationFilter implements Filter {
//    private static final String BASIC_AUTHENTICATION_TYPE = "Basic";
//    private static final int NUMBER_OF_AUTHENTICATION_FIELDS = 2;
//    private static final String ACCEPT_ALL_TYPES = "*";
//
//    private final AuthenticationDetails authenticationDetails;
//
//    public BasicAuthenticationFilter(final AuthenticationDetails authenticationDetails) {
//        this.authenticationDetails = authenticationDetails;
//    }
//
//    @Override
//    public void handle(final Request request, final Response response) {
//        Optional<String> maybeAuthorizationHeader = Optional.ofNullable(request.headers("Authorization"))
//                .filter(BasicAuthenticationFilter::isBasicAuthHeader)
//                .map(s -> s.substring(BASIC_AUTHENTICATION_TYPE.length()))
//                .map(String::trim);
//
//        if (notAuthenticatedWith(maybeAuthorizationHeader)) {
//            response.header("WWW-Authenticate", BASIC_AUTHENTICATION_TYPE);
//            halt(401);
//        }
//    }
//
//    private boolean notAuthenticatedWith(final Optional<String> encodedHeader) {
//        return !authenticatedWith(encodedHeader);
//    }
//
//    private boolean authenticatedWith(final Optional<String> encodedHeader) {
//        Supplier<Base64.Decoder> decoder = Base64::getDecoder;
//
//        return encodedHeader
//                .map(decoder.get()::decode) //decode
//                .map(String::new) //toString
//                .map(s -> s.split(":")) //split username and password
//                .filter(sa -> sa.length == NUMBER_OF_AUTHENTICATION_FIELDS) //check if valid
//                .map(sa -> new AuthenticationDetails(sa)) //to tuple
//                .map(c -> authenticationDetails.username.equals(c.username)
//                        && authenticationDetails.password.equals(c.password)) //verify
//                .orElse(false);
//    }
//
//    private static boolean isBasicAuthHeader(String s) {
//        return s.startsWith("Basic");
//    }
//}
