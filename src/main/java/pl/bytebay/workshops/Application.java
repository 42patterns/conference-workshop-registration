package pl.bytebay.workshops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.bytebay.workshops.auth.AuthenticationDetails;
import pl.bytebay.workshops.auth.BasicAuthenticationFilter;
import spark.ModelAndView;
import spark.Service;
import spark.template.handlebars.HandlebarsTemplateEngine;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.lang.System.getProperty;
import static java.util.Optional.ofNullable;

public class Application {

    private final static Logger LOG = LoggerFactory.getLogger(Application.class);
    private final int port;
    private final AuthenticationDetails authenticationDetails;

    public Application(Optional<String> maybePort, AuthenticationDetails authenticationDetails) {
        this.port = maybePort.filter(s -> s.matches("\\d+"))
                .map(Integer::valueOf)
                .orElse(8080);
        this.authenticationDetails = authenticationDetails;
    }

    public static void main(String[] args) {
        AuthenticationDetails auth = new AuthenticationDetails(ofNullable(getProperty("username")),
                ofNullable(getProperty("password")));
        Application app = new Application(ofNullable(getProperty("PORT")),
                auth);
        app.run();
    }

    private void run() {
        Service http = Service.ignite();
        http.port(this.port);

        http.get("/", (req, resp) -> {
            Map<String, Object> map = new HashMap();
            map.put("title", "Seasons of the year");
            map.put("seasons", Arrays.asList("Spring", "Summer", "Autumn", "Winter"));

            return new ModelAndView(map, "index.hbs");
        }, new HandlebarsTemplateEngine());

        http.before(new BasicAuthenticationFilter("/admin/*", authenticationDetails));
        http.post("/admin/updateSchedule", (req, resp) -> {
            resp.status(201);
            return "Success";
        });

    }
}
