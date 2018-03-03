package pl.bytebay.workshops;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.bytebay.workshops.agenda.ScheduleParser;
import pl.bytebay.workshops.agenda.model.Schedule;
import pl.bytebay.workshops.agenda.model.ScheduleDay;
import pl.bytebay.workshops.auth.AuthenticationDetails;
import pl.bytebay.workshops.auth.BasicAuthenticationFilter;
import pl.bytebay.workshops.view.BytebayHandlebarEngine;
import spark.ModelAndView;
import spark.Service;
import spark.template.handlebars.HandlebarsTemplateEngine;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.System.getProperty;
import static java.lang.System.getenv;
import static java.util.Optional.ofNullable;
import static spark.Spark.halt;

public class Application {

    private final static Logger LOG = LoggerFactory.getLogger(Application.class);
    private static String GITHUB_URL = "https://raw.githubusercontent.com/42patterns/bytebay-website/master/_data/";
    private final int port;
    private final AuthenticationDetails authenticationDetails;
    private final Jdbi jdbi;
    private final Map<String, String> usernameHashmap;
    private final Schedule schedule;

    public Application(Optional<String> maybePort,
                       Jdbi jdbi,
                       AuthenticationDetails authenticationDetails,
                       Schedule schedule,
                       Map<String, String> usernameHashmap) {
        this.port = maybePort.filter(s -> s.matches("\\d+"))
                .map(Integer::valueOf)
                .orElse(8080);
        this.jdbi = jdbi;
        this.authenticationDetails = authenticationDetails;
        this.schedule = schedule;
        this.usernameHashmap = usernameHashmap;
    }

    public static void main(String[] args) throws MalformedURLException, URISyntaxException {
        AuthenticationDetails auth = new AuthenticationDetails(ofNullable(getProperty("username")),
                ofNullable(getProperty("password")));
        ScheduleParser parser = new ScheduleParser(new URL(GITHUB_URL).toURI());
        UserDataParser userdata = new UserDataParser();
        Jdbi jdbi = Jdbi.create(ofNullable(getenv("JDBC_DATABASE_URL"))
                .orElseThrow(() -> new RuntimeException("No DATABASE_URL found")));

        // migrations
        jdbi.withHandle(h -> h.execute("CREATE TABLE IF NOT EXISTS sessions (" +
                "id SERIAL CONSTRAINT firstkey PRIMARY KEY, " +
                "hash VARCHAR(40)," +
                "\"sessions-2018-03-15-10:30\" VARCHAR(255)," +
                "\"sessions-2018-03-15-14:00\" VARCHAR(255)," +
                "\"sessions-2018-03-16-9:00\" VARCHAR(255)," +
                "\"sessions-2018-03-16-12:30\" VARCHAR(255)," +
                "insert_date TIMESTAMP DEFAULT now()" +
                ");"));



        Application app = new Application(ofNullable(getenv("PORT")),
                jdbi,
                auth,
                parser.schedule(),
                userdata.parse());
        app.run();
    }

    private void run() {
        Service http = Service.ignite();
        http.port(this.port);

        http.get("/mostPopularWorkshops", (req, resp) -> {

            Map<String, Integer> popularity = jdbi.withHandle(h -> h.createQuery("select s, count(*) from " +
                    "( " +
                    "select s1 as s from sessions where hash != 'test' " +
                    "union all select s2 as s from sessions where hash != 'test' " +
                    "union all select s3 as s from sessions where hash != 'test' " +
                    "union all select s4 as s from sessions where hash != 'test') " +
                    "all_sessions group by s order by count;")
                    .map((rs, ctx) -> new AbstractMap.SimpleEntry<>(rs.getString("s"), rs.getInt("count")))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

            popularity.remove(null);
            return popularity;
        }, new JsonTransformer());

        http.get("/:hash", (req, resp) -> {
            String hash = req.params("hash");

            if (!usernameHashmap.containsKey(hash)) {
                halt(401, "Invalid hash");
            }

            RowMapper<List<String>> mapper = (rs, statementContext) ->
                    Arrays.asList(rs.getString("s1"), rs.getString("s2"),
                            rs.getString("s3"), rs.getString("s4"));

            Map<String, Integer> popularity = jdbi.withHandle(h -> h.createQuery("select s, count(*) from " +
                    "( " +
                    "select s1 as s from sessions where hash != 'test' " +
                    "union all select s2 as s from sessions where hash != 'test' " +
                    "union all select s3 as s from sessions where hash != 'test' " +
                    "union all select s4 as s from sessions where hash != 'test') " +
                    "all_sessions group by s;\n")
                    .map((rs, ctx) -> new AbstractMap.SimpleEntry<>(rs.getString("s"), rs.getInt("count")))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

            Optional<List<String>> previous = jdbi.withHandle(h -> h
                    .createQuery("SELECT * FROM sessions WHERE hash=:hash ORDER BY insert_date DESC LIMIT 1")
                    .bind("hash", hash)
                    .map(mapper)
                    .findFirst());

            LOG.info("Previous registration for [hash={}]: {}", hash, previous);

            Map<String, Object> map = new HashMap();
            map.put("hash", hash);
            map.put("previous", previous.orElse(Collections.emptyList()));
            map.put("popularity", popularity);
            map.put("name", usernameHashmap.get(hash));
            map.put("isTest", ("test".equals(req.params("hash"))?true:false));
            map.put("schedule", schedule.getDays());
            return new ModelAndView(map, "index.hbs");
        }, new BytebayHandlebarEngine());


        http.post("/:hash", (req, resp) -> {

            String hash = req.params("hash");

            Map<String, Object> map = new HashMap<>();
            map.put("s1", req.queryMap().value("sessions-2018-03-15-10:30"));
            map.put("s2", req.queryMap().value("sessions-2018-03-15-14:00"));
            map.put("s3", req.queryMap().value("sessions-2018-03-16-9:00"));
            map.put("s4", req.queryMap().value("sessions-2018-03-16-12:30"));


            Integer i = jdbi.withHandle(h -> h
                    .createUpdate("INSERT INTO sessions VALUES(default, :hash, :s1, :s2, :s3, :s4, default)")
                    .bind("hash", hash)
                    .bindMap(map)
                    .execute()
            );

            LOG.info("Insert successful [rowCount={}, hash={}, data={}]", i, hash, map);
            resp.redirect("/" + req.params("hash"));
            return null;
        });


        http.before("/admin/*", new BasicAuthenticationFilter(authenticationDetails));
        http.get("/admin/registrations", (req, resp) -> {

            RowMapper<String> mapper = (rs, ctx) ->
                    Arrays.asList(rs.getString("hash"),
                            rs.getString("s1"), rs.getString("s2"),
                            rs.getString("s3"), rs.getString("s4"),
                            rs.getString("insert_date")
                    ).stream().collect(Collectors.joining(", "));

            List<String> results = jdbi.withHandle(h -> h.createQuery("select ranked.* from " +
                    "(" +
                    "select *, rank() over (partition by hash order by insert_date desc) as rank from sessions" +
                    ") as ranked " +
                    "where rank = 1 and hash != 'test")
                    .map(mapper)
                    .collect(Collectors.toList())
            );

            resp.type("text/plain");

            return results.stream().collect(Collectors.joining("\n"));
        });

    }
}
