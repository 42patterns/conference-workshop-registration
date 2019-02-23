package patterns42.workshops;

import io.javalin.BadRequestResponse;
import io.javalin.ForbiddenResponse;
import io.javalin.Javalin;
import lombok.Builder;
import lombok.Value;
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
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.System.getProperty;
import static java.lang.System.getenv;
import static java.util.Optional.ofNullable;

@Slf4j
public class Application {

    private final static Logger LOG = LoggerFactory.getLogger(Application.class);
    private final int port;
    private final AdminAuthenticationDetails authenticationDetails;
    private final Jdbi jdbi;
    private final Map<String, String> usernameHashmap;
    private final Schedule schedule;

    public static void main(String[] args) throws MalformedURLException {
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
                .json(popularity()));

        http.get("/:hash", ctx -> {
            String hash = ctx.pathParam("hash");

            if (!usernameHashmap.containsKey(hash)) {
                throw new ForbiddenResponse("Invalid hash");
            }

            List<String> previous = jdbi.withExtension(SessionDao.class, dao -> dao.previousSessions(hash));
            LOG.info("Previous registration for [hash={}]: {}", hash, previous);

            Map<String, Object> attrs = new HashMap();
            attrs.put("hash", hash);
            attrs.put("previous", previous);
            attrs.put("popularity", sessionsPopularityWithCapacity());
            attrs.put("name", usernameHashmap.get(hash));
            attrs.put("isTest", (UserDataParser.TEST_HASH_VALUE.equals(ctx.pathParam("hash")) ? true : false));
            attrs.put("schedule", schedule.getFirstDay());

            ctx.render("/templates/index.twig", attrs);
        });


        http.post("/:hash", ctx -> {

            String hash = ctx.pathParam("hash");
            if (!usernameHashmap.containsKey(hash)) {
                throw new ForbiddenResponse("Invalid hash");
            }

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

            //validation
            Map<String, SessionCapacity> sessionCapacityMap = sessionsPopularityWithCapacity(hash);
            Predicate<SessionDao.SessionDTO> isAboveCapacity = dto -> {
                SessionCapacity sessionCapacity = sessionCapacityMap.get(dto.getTitle());
                return (sessionCapacity.getCurrent() + 1 > sessionCapacity.getMax());
            };


            if (sessionDTOS.stream()
                    .filter(isAboveCapacity)
                    .count() > 0) {
                throw new BadRequestResponse("Invalid data. Some sessions might already got full");
            }

            int[] results = jdbi.withExtension(SessionDao.class, dao -> dao.insertSessions(hash, sessionDTOS));

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

    private Map<String, SessionCapacity> sessionsPopularityWithCapacity(String... filter_out_hash) {
        Map<String, SessionDao.PopularityRank> popularity = popularity(filter_out_hash).stream()
                .collect(Collectors.toMap(
                        SessionDao.PopularityRank::getTitle,
                        Function.identity()
                ));

        return schedule.getAllSessions().stream()
                .collect(Collectors.toMap(
                    Session::getTitle,
                    session -> {
                        SessionDao.PopularityRank popularityRank = popularity.getOrDefault(
                                session.getTitle(),
                                new SessionDao.PopularityRank(session.getTitle(), 0)
                        );

                        return SessionCapacity.builder().current(popularityRank.getCount()).max(session.getSeats()).build();
                    }
            ));
    }

    private List<SessionDao.PopularityRank> popularity(String... filter_out_hash) {
        List<String> filtered_out_hashes = new ArrayList<>(List.of(filter_out_hash));
        filtered_out_hashes.add(UserDataParser.TEST_HASH_VALUE);

        return jdbi.withExtension(SessionDao.class, dao -> dao.sessionsPopularity(filtered_out_hashes));
    }

}

@Value @Builder
class SessionCapacity {
    final Integer current;
    final Integer max;
}