package patterns42.workshops;

import io.javalin.ForbiddenResponse;
import io.javalin.Javalin;
import io.javalin.rendering.template.JavalinJtwig;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import patterns42.workshops.agenda.ScheduleParser;
import patterns42.workshops.agenda.model.Schedule;
import patterns42.workshops.agenda.model.Session;
import patterns42.workshops.auth.AdminAuthenticationDetails;
import patterns42.workshops.dao.SessionDao;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.System.getProperty;
import static java.lang.System.getenv;
import static java.util.Optional.ofNullable;
import static java.util.function.Predicate.not;

@Slf4j
public class Application {

    private final static Logger LOG = LoggerFactory.getLogger(Application.class);
    private final int port;
    private final AdminAuthenticationDetails authenticationDetails;
    private final Jdbi jdbi;
    private final Map<String, String> usernameHashmap;
    private final Schedule schedule;

    public static void main(String[] args) throws MalformedURLException, URISyntaxException {
        Integer port = ofNullable(getenv("PORT"))
                .filter(s -> s.matches("\\d+"))
                .map(Integer::valueOf)
                .orElse(8080);

        AdminAuthenticationDetails auth = new AdminAuthenticationDetails(ofNullable(getProperty("USERNAME")),
                ofNullable(getProperty("PASSWORD")));
        log.info("{}", auth);

        ScheduleParser parser = new ScheduleParser(ofNullable(getProperty("AGENDA_URL")));
        UserDataParser userdata = new UserDataParser();

        Jdbi jdbi = Jdbi.create(ofNullable(getenv("JDBC_DATABASE_URL"))
                .orElseThrow(() -> new RuntimeException("No JDBC_DATABASE_URL found")));
        jdbi.installPlugin(new SqlObjectPlugin());
        jdbi.useExtension(SessionDao.class, dao -> dao.createTable());

        new Application(port,
                jdbi,
                auth,
                parser.schedule(),
                userdata.parse()
        ).run();
    }

    public Application(Integer port,
                       Jdbi jdbi,
                       AdminAuthenticationDetails authenticationDetails,
                       Schedule schedule,
                       Map<String, String> usernameHashmap) {
        this.port = port;
        this.jdbi = jdbi;
        this.authenticationDetails = authenticationDetails;
        this.schedule = schedule;
        this.usernameHashmap = usernameHashmap;
    }

    private void run() {
        Javalin http = Javalin.create()
                .enableCorsForOrigin("*");
        http.port(this.port);

        http.get("/", ctx -> ctx
                .contentType("text/html;charset=UTF-8")
                .html("<h1>Registration app for <a href=\"https://segfault.events/gdansk2019/\">Segfault University GDN 2019</a><h1>"));

        http.get("/stats", ctx -> ctx.contentType("application/json")
                .json(popularityReport()));

        http.get("/:hash/myagenda", handler -> {
            String hash = handler.pathParam("hash");

//            if (!usernameHashmap.containsKey(hash)) {
//                handler.(401, "Invalid hash");
//            }

            RowMapper<Map<String, Session>> mapper = (rs, statementContext) -> {
                Map<String, Session> map = new LinkedHashMap<>();
//                map.put("Czwartek, 10:30", schedule.getAllSessions().getOrDefault(rs.getInt("id_1"), null));
//                map.put("Czwartek, 14:00", schedule.getAllSessions().getOrDefault(rs.getInt("id_2"), null));
//                map.put("Piątek, 9:00", schedule.getAllSessions().getOrDefault(rs.getInt("id_3"), null));
//                map.put("Piątek, 12:30", schedule.getAllSessions().getOrDefault(rs.getInt("id_4"), null));
                return map;
            };

            Map<String, Session> agenda = Collections.emptyMap();
//            jdbi.withHandle(h -> h
//                    .createQuery("SELECT * FROM sessions WHERE hash=:hash ORDER BY insert_date DESC LIMIT 1")
//                    .bind("hash", hash)
//                    .map(mapper)
//                    .findFirst()).orElse(Collections.emptyMap());

            Map<String, Object> map = new HashMap();
            map.put("hash", hash);
            map.put("name", usernameHashmap.get(hash));
            map.put("agenda", agenda);
//            return new ModelAndView(map, "myagenda.hbs");
        });


        http.get("/:hash", ctx -> {
            String hash = ctx.pathParam("hash");

            if (!usernameHashmap.containsKey(hash)) {
                throw new ForbiddenResponse("Invalid hash");
            }

            RowMapper<List<String>> mapper = (rs, statementContext) ->
                    Arrays.asList(rs.getString("title_1"), rs.getString("title_2"),
                            rs.getString("title_3"), rs.getString("title_4"));

            List<String> previous = jdbi.withExtension(SessionDao.class, dao -> dao.previousSessions(hash));
            LOG.info("Previous registration for [hash={}]: {}", hash, previous);

            Map<String, Object> attrs = new HashMap();
            attrs.put("hash", hash);
            attrs.put("previous", previous);
//            map.put("popularity", popularityFlatten());
            attrs.put("name", usernameHashmap.get(hash));
            attrs.put("isTest", (UserDataParser.TEST_HASH_VALUE.equals(ctx.pathParam("hash")) ? true : false));
            attrs.put("schedule", schedule.getFirstDay());

            ctx.render("/templates/index.twig", attrs);
        });


        http.post("/:hash", ctx -> {

            String hash = ctx.pathParam("hash");
            String session2 = ctx.formParam("session-2");
            String session4 = ctx.formParam("session-4");

            List<SessionDao.SessionDTO> sessionDTOS = Arrays.asList(SessionDao.SessionDTO.builder()
                            .sessionId(2)
                            .title(session2)
                            .build(),
                    SessionDao.SessionDTO.builder()
                            .sessionId(4)
                            .title(session4)
                            .build()
            );

//            Function<String, Session> sessionData = key -> Optional.ofNullable(req.queryMap().value(key))
//                    .map(Integer::valueOf)
//                    .map(id -> schedule.getAllSessions().get(id))
//                    .orElse(new Session());
//
//            List<Session> sessions = keys.stream()
//                    .map(sessionData)
//                    .collect(Collectors.toList());
//
//            validation
//            Map<String, Integer> popularity = popularityFlatten(hash);
//            if (sessions.stream()
//                    .filter(s -> Objects.nonNull(s.getId()))
//                    .filter(s -> ((popularity.getOrDefault(s.getTitle(), 0) + 1) > s.getCapacity()))
//                    .count() > 0) {
//                halt(400, "Invalid sessions data. Try again");
//                return null;
//            }


//            final Map<String,Object> map = new HashMap<>();
//            IntStream.rangeClosed(1, sessions.size()).forEach(idx -> {
//                map.put("id"+idx, sessions.get(idx-1).getId());
//                map.put("title"+idx, sessions.get(idx-1).getTitle());
//            });

            int[] results = jdbi.withExtension(SessionDao.class, dao -> dao.insertSessions(hash, sessionDTOS));

//            Integer i = jdbi.withHandle(h -> h
//                    .createUpdate("INSERT INTO sessions (hash, " +
//                            "   id_1, title_1, " +
//                            "   id_2, title_2, " +
//                            "   id_3, title_3, " +
//                            "   id_4, title_4 " +
//                            ") " +
//                            "VALUES(:hash, :id1, :title1, :id2, :title2, :id3, :title3, :id4, :title4)")
//                    .bind("hash", hash)
//                    .bindMap(map)
//                    .execute()
//            );
//
            LOG.info("Insert successful [rowCount={}, hash={}, data={}]", results, hash, sessionDTOS);

            ctx.redirect("/" + ctx.pathParam("hash"));
        });

//        http.before("/admin/*", new BasicAuthenticationFilter(authenticationDetails));
        http.get("/admin/registrations", handler -> {

            RowMapper<String> mapper = (rs, ctx) ->
                    Arrays.asList(rs.getString("hash"),
                            rs.getString("id_1"), rs.getString("id_2"),
                            rs.getString("id_3"), rs.getString("id_4"),
                            rs.getString("title_1"), rs.getString("title_2"),
                            rs.getString("title_3"), rs.getString("title_4"),
                            rs.getString("insert_date")
                    ).stream().collect(Collectors.joining("##"));

//            List<String> results = jdbi.withHandle(h -> h.createQuery("select ranked.* from " +
//                    "(" +
//                    "select *, rank() over (partition by hash order by insert_date desc) as rank from sessions" +
//                    ") as ranked " +
//                    "where rank = 1 and hash != 'test'")
//                    .map(mapper)
//                    .collect(Collectors.toList())
//            );

            handler.contentType("text/plain");

//            return results.stream().collect(Collectors.joining("\n"));
        });

        http.start();

    }

    private Map<String, Integer> popularityFlatten() {
        return popularityFlatten("");
    }


    private Map<String, Integer> popularityFlatten(String filter_out_hash) {
        return popularity(filter_out_hash)
                .stream()
                .collect(Collectors.toMap(m -> m.get("s").toString(), m -> Integer.valueOf(m.get("count").toString())));
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
        return Collections.emptyList();
//        return jdbi.withHandle(h -> h.createQuery(String.format("select rank, s, id, count(*) from " +
//                "( " +
//                "select distinct hash, id_1 as id, title_1 as s, rank() over (partition by hash order by insert_date desc) as rank  from sessions %1$s  " +
//                "union all select distinct hash, id_2 as id, title_2 as s, rank() over (partition by hash order by insert_date desc) as rank from sessions %1$s " +
//                "union all select distinct hash, id_3 as id, title_3 as s, rank() over (partition by hash order by insert_date desc) as rank from sessions %1$s  " +
//                "union all select distinct hash, id_4 as id, title_4 as s, rank() over (partition by hash order by insert_date desc) as rank from sessions %1$s) " +
//                "all_sessions where s!='' and rank=1 group by rank, id, s order by count desc;\n", whereClause))
//                .mapToMap()
//                .list()
//        );
    }

}
