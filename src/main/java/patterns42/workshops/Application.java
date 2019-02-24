package patterns42.workshops;

import io.javalin.*;
import io.javalin.security.Role;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import patterns42.workshops.agenda.ScheduleParser;
import patterns42.workshops.agenda.model.Schedule;
import patterns42.workshops.agenda.model.Session;
import patterns42.workshops.auth.AdminAuthenticationDetails;
import patterns42.workshops.dao.SessionDao;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.System.getenv;
import static java.util.Optional.ofNullable;

public class Application {

    private final int port;
    private final Controllers controllers;

    public static void main(String[] args) throws MalformedURLException {
        Integer port = ofNullable(getenv("PORT"))
                .filter(s -> s.matches("\\d+"))
                .map(Integer::valueOf)
                .orElse(8080);

        AdminAuthenticationDetails auth = new AdminAuthenticationDetails(ofNullable(getenv("USERNAME")),
                ofNullable(getenv("PASSWORD")));

        ScheduleParser parser = new ScheduleParser(ofNullable(getenv("AGENDA_URL")));

        InputStreamReader reader = new InputStreamReader(Application.class.getResourceAsStream("/userdata.csv"));
        BufferedReader bufferedReader = new BufferedReader(reader);
        UserDataParser userdata = new UserDataParser(bufferedReader);

        Jdbi jdbi = Jdbi.create(ofNullable(getenv("JDBC_DATABASE_URL"))
                .orElseThrow(() -> new RuntimeException("No JDBC_DATABASE_URL found")));
        jdbi.installPlugin(new SqlObjectPlugin());
        jdbi.useExtension(SessionDao.class, dao -> dao.createTable());

        Controllers controllers = new Controllers(jdbi,
                auth,
                parser.schedule(),
                userdata.parse()
        );
        new Application(port, controllers).run();
    }

    public Application(Integer port, Controllers controllers) {
        this.port = port;
        this.controllers = controllers;
    }

    private void run() {
        Javalin http = Javalin.create()
                .enableCorsForOrigin("*");
        http.port(this.port);
        http.accessManager(controllers::accessManager);
        http.get("/", controllers::rootPath);
        http.get("/stats", controllers::statistics);
        http.get("/:hash", controllers::chooseSessions);
        http.post("/:hash", controllers::saveSessions);
        http.get("/admin/registrations",
                controllers::getAllRegistrations,
                Set.of(AdminAuthenticationDetails.Authed.ADMIN));
        http.start();
    }
}

@Slf4j
class Controllers {
    private final Jdbi jdbi;
    private final AdminAuthenticationDetails authenticationDetails;
    private final Schedule schedule;
    private final Map<UserDataParser.Hash, UserDataParser.Username> usernameHashmap;

    public Controllers(Jdbi jdbi, AdminAuthenticationDetails authenticationDetails, Schedule schedule, Map<UserDataParser.Hash, UserDataParser.Username> usernameHashmap) {
        this.jdbi = jdbi;
        this.authenticationDetails = authenticationDetails;
        this.schedule = schedule;
        this.usernameHashmap = usernameHashmap;
    }

    public void accessManager(Handler handler, Context ctx, Set<Role> permittedRoles) throws Exception {
        if (permittedRoles.isEmpty() ||
                (!permittedRoles.isEmpty() && this.authenticationDetails.authorize(ctx.basicAuthCredentials()))) {
            handler.handle(ctx);
        } else {
            throw new UnauthorizedResponse("Unauthorized");
        }
    }

    public void rootPath(Context ctx) {
        ctx.contentType("text/html;charset=UTF-8")
                .html("<h1>Registration app for <a href=\"https://segfault.events/gdansk2019/\">Segfault University GDN 2019</a><h1>");
    }

    public void statistics(Context context) {
        context.contentType("application/json")
                .json(popularity());
    }

    public void chooseSessions(Context ctx) {
        UserDataParser.Hash hash = UserDataParser.Hash.of(ctx.pathParam("hash"));
        if (!usernameHashmap.containsKey(hash)) {
            throw new ForbiddenResponse("Invalid hash");
        }

        List<String> previous = jdbi.withExtension(SessionDao.class, dao -> dao.previousSessions(hash.getHash()));
        log.info("Previous registration for [hash={}]: {}", hash, previous);

        Map<String, Object> attrs = new HashMap();
        attrs.put("hash", hash.getHash());
        attrs.put("previous", previous);
        attrs.put("popularity", sessionsPopularityWithCapacity());
        attrs.put("name", usernameHashmap.get(hash).getUsername());
        attrs.put("isTest", (UserDataParser.TEST_HASH.equals(hash) ? true : false));
        attrs.put("schedule", schedule.getFirstDay());

        ctx.render("/templates/index.twig", attrs);
    }

    public void saveSessions(Context ctx) {
        UserDataParser.Hash hash = UserDataParser.Hash.of(ctx.pathParam("hash"));
        if (!usernameHashmap.containsKey(hash)) {
            throw new ForbiddenResponse("Invalid hash");
        }

        String session2 = ctx.formParam("session-2");
        String session4 = ctx.formParam("session-4");

        List<SessionDao.SessionDto> sessionDTOS = Arrays.asList(SessionDao.SessionDto.builder()
                        .sessionId(2)
                        .title(session2)
                        .build(),
                SessionDao.SessionDto.builder()
                        .sessionId(4)
                        .title(session4)
                        .build()
        );

        //validation
        Map<String, SessionCapacity> sessionCapacityMap = sessionsPopularityWithCapacity(hash.getHash());
        Predicate<SessionDao.SessionDto> isAboveCapacity = dto -> {
            SessionCapacity sessionCapacity = sessionCapacityMap.get(dto.getTitle());
            return (sessionCapacity.getCurrent() + 1 > sessionCapacity.getMax());
        };


        if (sessionDTOS.stream()
                .filter(isAboveCapacity)
                .count() > 0) {
            throw new BadRequestResponse("Invalid data. Some sessions might already got full");
        }

        int[] results = jdbi.withExtension(SessionDao.class, dao -> dao.insertSessions(hash.getHash(), sessionDTOS));

        log.info("Insert successful [rowCount={}, hash={}, data={}]", results, hash, sessionDTOS);

        ctx.redirect("/" + ctx.pathParam("hash"));
    }

    public void getAllRegistrations(Context ctx) {
        List<SessionDao.RegistrationDto> registrationDtos = jdbi.withExtension(SessionDao.class,
                dao -> dao.allRegistrations(List.of(UserDataParser.TEST_HASH.getHash())));

        ctx.contentType("text/plain")
                .result(registrationDtos.stream()
                        .map(dto -> String.join("###", dto.getHash(), dto.getTitle(), dto.getDate().format(DateTimeFormatter.ISO_DATE_TIME)))
                        .reduce("", (l, r) -> String.join("\n", l, r)));
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
        filtered_out_hashes.add(UserDataParser.TEST_HASH.getHash());

        return jdbi.withExtension(SessionDao.class, dao -> dao.sessionsPopularity(filtered_out_hashes));
    }
}

@Value
@Builder
class SessionCapacity {
    final Integer current;
    final Integer max;
}