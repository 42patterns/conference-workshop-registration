package pl.bytebay.workshops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.bytebay.workshops.agenda.ScheduleParser;
import pl.bytebay.workshops.auth.AuthenticationDetails;
import pl.bytebay.workshops.auth.BasicAuthenticationFilter;
import spark.ModelAndView;
import spark.Service;
import spark.template.handlebars.HandlebarsTemplateEngine;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static java.lang.System.getProperty;
import static java.lang.System.getenv;
import static java.util.Optional.ofNullable;

public class Application {

    private final static Logger LOG = LoggerFactory.getLogger(Application.class);
    private static String GITHUB_URL = "https://raw.githubusercontent.com/42patterns/bytebay-website/master/_data/";
    private final int port;
    private final AuthenticationDetails authenticationDetails;
    private final ScheduleParser scheduleParser;

    public Application(Optional<String> maybePort,
                       AuthenticationDetails authenticationDetails,
                       ScheduleParser scheduleParser) {
        this.port = maybePort.filter(s -> s.matches("\\d+"))
                .map(Integer::valueOf)
                .orElse(8080);
        this.authenticationDetails = authenticationDetails;
        this.scheduleParser = scheduleParser;
    }

    public static void main(String[] args) throws MalformedURLException, URISyntaxException {
        AuthenticationDetails auth = new AuthenticationDetails(ofNullable(getProperty("username")),
                ofNullable(getProperty("password")));
        ScheduleParser parser = new ScheduleParser(new URL(GITHUB_URL).toURI());
        Application app = new Application(ofNullable(getenv("PORT")),
                auth,
                parser);
        app.run();
    }

    private void run() {
        Service http = Service.ignite();
        http.port(this.port);

        http.get("/:hash", (req, resp) -> {
            Map<String, Object> map = new HashMap();
            map.put("hash", req.params("hash"));
            map.put("schedule", scheduleParser.schedule());
            return new ModelAndView(map, "index.hbs");
        }, new HandlebarsTemplateEngine());

        http.before("/admin/*", new BasicAuthenticationFilter(authenticationDetails));
        http.post("/admin/updateSchedule", (req, resp) -> {
            LOG.info("Foo!");
            resp.status(201);
            return "Success";
        });

    }
}
