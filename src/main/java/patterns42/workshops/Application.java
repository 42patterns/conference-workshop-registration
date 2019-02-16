package patterns42.workshops;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import patterns42.workshops.agenda.ScheduleParser;
import patterns42.workshops.agenda.model.Schedule;
import patterns42.workshops.agenda.model.Session;
import patterns42.workshops.auth.AuthenticationDetails;
import patterns42.workshops.auth.BasicAuthenticationFilter;
import patterns42.workshops.view.BytebayHandlebarEngine;
import spark.ModelAndView;
import spark.Service;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
                "id_1 SMALLINT," +
                "title_1 VARCHAR(255)," +
                "id_2 SMALLINT," +
                "title_2 VARCHAR(255)," +
                "id_3 SMALLINT," +
                "title_3 VARCHAR(255)," +
                "id_4 SMALLINT," +
                "title_4 VARCHAR(255)," +
                "insert_date TIMESTAMP DEFAULT now()" +
                ");"));

//        /* Migrating IDs */
//        Schedule schedule = parser.schedule();
//        jdbi.useHandle(h -> {
//            List<Map<String, Object>> maps = h.createQuery("select * from sessions")
//                    .mapToMap()
//                    .list();
//
//            Function<Optional<String>, Optional<Integer>> getSessionId = maybeTitle -> maybeTitle
//                    .map(title -> schedule
//                            .getAllSessions().values().stream()
//                            .filter(s -> s.getTitle().equals(title))
//                            .findFirst()
//                            .map(Session::getId).get()
//                    );
//
//            Function<Object, Optional<String>> arg = o -> Optional.ofNullable(o)
//                    .map(Object::toString);
//
//            for (Map<String,Object> map: maps) {
//                h.createUpdate("update sessions set id_1=?, id_2=?,id_3=?,id_4=? where id=?")
//                        .bind(0, getSessionId.apply(arg.apply(map.get("title_1"))))
//                        .bind(1, getSessionId.apply(arg.apply(map.get("title_2"))))
//                        .bind(2, getSessionId.apply(arg.apply(map.get("title_3"))))
//                        .bind(3, getSessionId.apply(arg.apply(map.get("title_4"))))
//                        .bind(4, map.get("id"))
//                        .execute();
//            }
//        });

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

        http.after("/mostPopularWorkshops", (request, response) -> {
            final HashMap<String, String> corsHeaders = new HashMap<>();

            corsHeaders.put("Access-Control-Allow-Methods", "GET");
            corsHeaders.put("Access-Control-Allow-Origin", "*");
            corsHeaders.put("Access-Control-Allow-Headers", "Content-Type,Authorization,X-Requested-With,Content-Length,Accept,Origin,");
            corsHeaders.put("Access-Control-Allow-Credentials", "true");
            corsHeaders.forEach((key, value) -> {
                response.header(key, value);
            });
        });
        http.get("/mostPopularWorkshops", (req, resp) -> popularityReport(), new JsonTransformer());

        http.get("/:hash/myagenda", (req, resp) -> {
            String hash = req.params("hash");

            if (!usernameHashmap.containsKey(hash)) {
                halt(401, "Invalid hash");
            }

            RowMapper<Map<String, Session>> mapper = (rs, statementContext) -> {
                Map<String, Session> map = new LinkedHashMap<>();
                map.put("Czwartek, 10:30", schedule.getAllSessions().getOrDefault(rs.getInt("id_1"), null));
                map.put("Czwartek, 14:00", schedule.getAllSessions().getOrDefault(rs.getInt("id_2"), null));
                map.put("Piątek, 9:00", schedule.getAllSessions().getOrDefault(rs.getInt("id_3"), null));
                map.put("Piątek, 12:30", schedule.getAllSessions().getOrDefault(rs.getInt("id_4"), null));
                return map;
            };

            Map<String, Session> agenda = jdbi.withHandle(h -> h
                    .createQuery("SELECT * FROM sessions WHERE hash=:hash ORDER BY insert_date DESC LIMIT 1")
                    .bind("hash", hash)
                    .map(mapper)
                    .findFirst()).orElse(Collections.emptyMap());

            Map<String, Object> map = new HashMap();
            map.put("hash", hash);
            map.put("name", usernameHashmap.get(hash));
            map.put("agenda", agenda);
            return new ModelAndView(map, "myagenda.hbs");
        }, new BytebayHandlebarEngine());


        http.get("/:hash", (req, resp) -> {
            String hash = req.params("hash");

            if (!usernameHashmap.containsKey(hash)) {
                halt(401, "Invalid hash");
            }


            RowMapper<List<String>> mapper = (rs, statementContext) ->
                    Arrays.asList(rs.getString("title_1"), rs.getString("title_2"),
                            rs.getString("title_3"), rs.getString("title_4"));

            Optional<List<String>> previous = jdbi.withHandle(h -> h
                    .createQuery("SELECT * FROM sessions WHERE hash=:hash ORDER BY insert_date DESC LIMIT 1")
                    .bind("hash", hash)
                    .map(mapper)
                    .findFirst());

            LOG.info("Previous registration for [hash={}]: {}", hash, previous);

            Map<String, Object> map = new HashMap();
            map.put("hash", hash);
            map.put("previous", previous.orElse(Collections.emptyList()));
            map.put("popularity", popularityFlatten());
            map.put("name", usernameHashmap.get(hash));
            map.put("isTest", ("test".equals(req.params("hash"))?true:false));
            map.put("schedule", schedule.getDays());
            return new ModelAndView(map, "index.hbs");
        }, new BytebayHandlebarEngine());


        http.post("/:hash", (req, resp) -> {

            String hash = req.params("hash");
            List<String> keys = Arrays.asList("sessions-2018-03-15-10:30",
                    "sessions-2018-03-15-14:00",
                    "sessions-2018-03-16-9:00",
                    "sessions-2018-03-16-12:30");

            Function<String, Session> sessionData = key -> Optional.ofNullable(req.queryMap().value(key))
                    .map(Integer::valueOf)
                    .map(id -> schedule.getAllSessions().get(id))
                    .orElse(new Session());

            List<Session> sessions = keys.stream()
                    .map(sessionData)
                    .collect(Collectors.toList());

            //validation
            Map<String, Integer> popularity = popularityFlatten(hash);
            if (sessions.stream()
                    .filter(s -> Objects.nonNull(s.getId()))
                    .filter(s -> ((popularity.getOrDefault(s.getTitle(), 0) + 1) > s.getCapacity()))
                    .count() > 0) {
                halt(400, "Invalid sessions data. Try again");
                return null;
            }


            final Map<String,Object> map = new HashMap<>();
            IntStream.rangeClosed(1, sessions.size()).forEach(idx -> {
                map.put("id"+idx, sessions.get(idx-1).getId());
                map.put("title"+idx, sessions.get(idx-1).getTitle());
            });

            Integer i = jdbi.withHandle(h -> h
                    .createUpdate("INSERT INTO sessions (hash, " +
                            "   id_1, title_1, " +
                            "   id_2, title_2, " +
                            "   id_3, title_3, " +
                            "   id_4, title_4 " +
                            ") " +
                            "VALUES(:hash, :id1, :title1, :id2, :title2, :id3, :title3, :id4, :title4)")
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
                            rs.getString("id_1"), rs.getString("id_2"),
                            rs.getString("id_3"), rs.getString("id_4"),
                            rs.getString("title_1"), rs.getString("title_2"),
                            rs.getString("title_3"), rs.getString("title_4"),
                            rs.getString("insert_date")
                    ).stream().collect(Collectors.joining("##"));

            List<String> results = jdbi.withHandle(h -> h.createQuery("select ranked.* from " +
                    "(" +
                    "select *, rank() over (partition by hash order by insert_date desc) as rank from sessions" +
                    ") as ranked " +
                    "where rank = 1 and hash != 'test'")
                    .map(mapper)
                    .collect(Collectors.toList())
            );

            resp.type("text/plain");

            return results.stream().collect(Collectors.joining("\n"));
        });

    }

    private Map<String, Integer> popularityFlatten() {
        return popularityFlatten("");
    }


    private Map<String, Integer> popularityFlatten(String filter_out_hash) {
        return popularity(filter_out_hash)
                .stream()
                .collect(Collectors.toMap(m -> m.get("s").toString(), m->Integer.valueOf(m.get("count").toString())));
    }

    private Map<Object, Map<String, Object>> popularityReport() {
        return popularity("")
                .stream().collect(Collectors.toMap(m -> m.get("id"), Function.identity()));
    }

    private List<Map<String, Object>> popularity(String filter_out_hash) {

        String whereClause = Arrays.asList("test", filter_out_hash)
                .stream()
                .filter(not(String::isEmpty))
                .map(s -> String.format("hash != '%s'", s))
                .collect(Collectors.joining(" AND ", "where ", ""));

        return jdbi.withHandle(h -> h.createQuery(String.format("select rank, s, id, count(*) from " +
                "( " +
                "select distinct hash, id_1 as id, title_1 as s, rank() over (partition by hash order by insert_date desc) as rank  from sessions %1$s  " +
                "union all select distinct hash, id_2 as id, title_2 as s, rank() over (partition by hash order by insert_date desc) as rank from sessions %1$s " +
                "union all select distinct hash, id_3 as id, title_3 as s, rank() over (partition by hash order by insert_date desc) as rank from sessions %1$s  " +
                "union all select distinct hash, id_4 as id, title_4 as s, rank() over (partition by hash order by insert_date desc) as rank from sessions %1$s) " +
                "all_sessions where s!='' and rank=1 group by rank, id, s order by count desc;\n", whereClause))
                .mapToMap()
                .list()
        );
    }

    static <T> Predicate<T> not(Predicate<T> p) { return p.negate(); }
}