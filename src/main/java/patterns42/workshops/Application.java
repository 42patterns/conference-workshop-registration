package patterns42.workshops;

import io.javalin.BadRequestResponse;
import io.javalin.Context;
import io.javalin.ForbiddenResponse;
import io.javalin.Handler;
import io.javalin.Javalin;
import io.javalin.UnauthorizedResponse;
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
import patterns42.workshops.dao.SessionsDao;
import patterns42.workshops.dao.UsersDao;

import java.net.MalformedURLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

        UserDataParser userdata = new UserDataParser();

        Jdbi jdbi = Jdbi.create(ofNullable(getenv("JDBC_DATABASE_URL"))
                .orElseThrow(() -> new RuntimeException("No JDBC_DATABASE_URL found")));
        jdbi.installPlugin(new SqlObjectPlugin());
        jdbi.useExtension(SessionsDao.class, dao -> dao.createSessionsTable());
        jdbi.useExtension(UsersDao.class, dao -> dao.createUsersTable());

        Controllers controllers = new Controllers(jdbi,
                auth,
                parser.schedule(),
                userdata
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
        http.post("/admin/userdata",
                controllers::updateUserData,
                Set.of(AdminAuthenticationDetails.Authed.ADMIN));
        http.start();
    }
}

@Slf4j
class Controllers {
    private final Jdbi jdbi;
    private final AdminAuthenticationDetails authenticationDetails;
    private final Schedule schedule;
    private final UserDataParser userdata;

    public Controllers(Jdbi jdbi, AdminAuthenticationDetails authenticationDetails, Schedule schedule, UserDataParser userdata) {
        this.jdbi = jdbi;
        this.authenticationDetails = authenticationDetails;
        this.schedule = schedule;
        this.userdata = userdata;
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
                .html("<h1>Registration app for <a href=\"https://segfault.events/warszawa2020/\">Segfault University Warszawa 2020</a><h1>");
    }

    public void statistics(Context context) {
        context.contentType("application/json")
                .json(popularity());
    }

    public void chooseSessions(Context ctx) {
        UsersDao.User user = Optional.ofNullable(
                jdbi.withExtension(UsersDao.class, dao -> dao.getUser(ctx.pathParam("hash")))
        ).orElseThrow(() -> new ForbiddenResponse("Invalid hash"));

        List<String> previous = jdbi.withExtension(SessionsDao.class, dao -> dao.previousSessions(user.getHash()));
        log.info("Previous registration for [hash={}]: {}", user, previous);

        Map<String, Object> attrs = new HashMap();
        attrs.put("hash", user.getHash());
        attrs.put("previous", previous);
        attrs.put("popularity", sessionsPopularityWithCapacity());
        attrs.put("name", user.getName());
        attrs.put("isTest", (UserDataParser.TEST_USER.equals(user) ? true : false));
        attrs.put("schedule", schedule.getSecondDay());

        ctx.render("/templates/index.twig", attrs);
    }

    public void saveSessions(Context ctx) {
        UsersDao.User user = Optional.ofNullable(
                jdbi.withExtension(UsersDao.class, dao -> dao.getUser(ctx.pathParam("hash")))
        ).orElseThrow(() -> new ForbiddenResponse("Invalid hash"));

        String session2 = ctx.formParam("session-2");
        String session4 = ctx.formParam("session-4");

        List<SessionsDao.SessionDto> sessionDTOS = Arrays.asList(SessionsDao.SessionDto.builder()
                        .sessionId(2)
                        .title(session2)
                        .build(),
                SessionsDao.SessionDto.builder()
                        .sessionId(4)
                        .title(session4)
                        .build()
        );

        //validation
        Map<String, SessionCapacity> sessionCapacityMap = sessionsPopularityWithCapacity(user.getHash());
        Predicate<SessionsDao.SessionDto> isAboveCapacity = dto -> {
            SessionCapacity sessionCapacity = sessionCapacityMap.get(dto.getTitle());
            return (sessionCapacity.getCurrent() + 1 > sessionCapacity.getMax());
        };


        if (sessionDTOS.stream()
                .filter(isAboveCapacity)
                .count() > 0) {
            throw new BadRequestResponse("Invalid data. Some sessions might already got full");
        }

        int[] results = jdbi.withExtension(SessionsDao.class, dao -> dao.insertSessions(user.getHash(), sessionDTOS));

        log.info("Insert successful [rowCount={}, hash={}, data={}]", results, user, sessionDTOS);

        ctx.redirect("/" + ctx.pathParam("hash"));
    }



    public void getAllRegistrations(Context ctx) {
        List<SessionsDao.RegistrationDto> registrationDtos = jdbi.withExtension(SessionsDao.class,
                dao -> dao.allRegistrations(List.of(UserDataParser.TEST_USER.getHash())));

        ctx.contentType("text/plain")
                .result(registrationDtos.stream()
                        .map(dto -> String.join("###", dto.getHash(), dto.getTitle(), dto.getDate().format(DateTimeFormatter.ISO_DATE_TIME)))
                        .map(str -> new StringBuilder()
                                        .append("=SPLIT(\"").append(str)
                                .append("\"; \"###\"; TRUE; TRUE)")
                                .toString()
                        )
                        .reduce("", (l, r) -> String.join("\n", l, r)));
    }

    public void updateUserData(Context ctx) {
        UserDataParser userDataParser = new UserDataParser();
        List<UsersDao.User> userList = userDataParser.parse(ctx.body());

        int[] rowCount = jdbi.withExtension(UsersDao.class, dao -> dao.insertUserHash(userList));
        log.info("Insert successful users.size()={}", rowCount.length);

        ctx.status(201).json(Map.of(
                "posted", rowCount.length,
                "total", jdbi.withExtension(UsersDao.class, dao -> dao.allUsers().size())));
    }

    private Map<String, SessionCapacity> sessionsPopularityWithCapacity(String... filter_out_hash) {
        Map<String, SessionsDao.PopularityRank> popularity = popularity(filter_out_hash).stream()
                .collect(Collectors.toMap(
                        SessionsDao.PopularityRank::getTitle,
                        Function.identity()
                ));

        return schedule.getAllSessions().stream()
                .filter(Session::isWorkshop)
                .collect(Collectors.toMap(
                        Session::getTitle,
                        session -> {
                            SessionsDao.PopularityRank popularityRank = popularity.getOrDefault(
                                    session.getTitle(),
                                    new SessionsDao.PopularityRank(session.getTitle(), 0)
                            );

                            return SessionCapacity.builder().current(popularityRank.getCount()).max(session.getSeats()).build();
                        },
                        (s1, s2) -> s1
                ));
    }

    private List<SessionsDao.PopularityRank> popularity(String... filter_out_hash) {
        List<String> filtered_out_hashes = new ArrayList<>(List.of(filter_out_hash));
        filtered_out_hashes.add(UserDataParser.TEST_USER.getHash());

        return jdbi.withExtension(SessionsDao.class, dao -> dao.sessionsPopularity(filtered_out_hashes));
    }

}

@Value
@Builder
class SessionCapacity {
    final Integer current;
    final Integer max;
}