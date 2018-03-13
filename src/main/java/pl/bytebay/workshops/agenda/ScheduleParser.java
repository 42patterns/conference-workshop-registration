package pl.bytebay.workshops.agenda;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.bytebay.workshops.agenda.model.Schedule;
import pl.bytebay.workshops.agenda.model.ScheduleDay;
import pl.bytebay.workshops.agenda.model.Session;
import pl.bytebay.workshops.agenda.model.Speaker;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ScheduleParser {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduleParser.class);
    private final String SPEAKERS_FILE = "speakers.yml";
    private final String SESSIONS_FILE = "sessions.yml";
    private final String SCHEDULE_FILE = "schedule.yml";
    private final URI root;

    public ScheduleParser(URI rootLocation) {
        this.root = rootLocation;
    }

    public static <T> Predicate<T> not(Predicate<T> t) {
        return t.negate();
    }

    public Schedule schedule() {
        return schedule(sessions(speakers()));
    }

    public Schedule schedule(Map<Integer, Session> sessions) {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(ScheduleDay.Timeslot.class, new TimeslotDeserializer(sessions));

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                .registerModule(new JavaTimeModule())
                .registerModule(module)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try {
            ScheduleDay[] scheduleDays = mapper.readValue(getFile(SCHEDULE_FILE), ScheduleDay[].class);
            return new Schedule(scheduleDays);
        } catch (IOException e) {
            LOG.warn("Error parsing {}", SPEAKERS_FILE, e);
            return new Schedule();
        }
    }

    public Map<Integer, Session> sessions(Map<Integer, Speaker> speakers) {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Session.class, new SessionDeserializer(speakers));
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .registerModule(module);

        try {

            JsonNode sessionsNode = mapper.readTree(getFile(SESSIONS_FILE))
                    .at("/list");
            Session[] sessions = mapper.treeToValue(sessionsNode, Session[].class);
            return Arrays.stream(sessions)
                    .filter(not(Session::getService))
                    .collect(Collectors.toMap(Session::getId, Function.identity()));
        } catch (IOException e) {
            LOG.warn("Error parsing {}", SESSIONS_FILE, e);
            return Collections.emptyMap();
        }
    }

    public Map<Integer, Speaker> speakers() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            Speaker[] speakers = mapper.readValue(getFile(SPEAKERS_FILE), Speaker[].class);
            return Arrays.stream(speakers)
                    .collect(Collectors.toMap(Speaker::getId, Function.identity()));
        } catch (IOException e) {
            LOG.warn("Error parsing {}", SPEAKERS_FILE, e);
            return Collections.emptyMap();
        }
    }

    private InputStream getFile(String file) throws IOException {
        try {
            LOG.info("Opening resource {}", root.resolve(file));
            return root.resolve(file).toURL().openStream();
        } catch (UnknownHostException e) {
            LOG.warn("Error resolving {}. Loading local file", root.resolve(file));
            return getClass().getResourceAsStream("/session-data/" + file);
        }
    }
}

class SessionDeserializer extends StdDeserializer<Session> {

    private final Map<Integer, Speaker> speakers;

    public SessionDeserializer(Map<Integer, Speaker> speakers) {
        super(Session.class);
        this.speakers = speakers;
    }

    @Override
    public Session deserialize(JsonParser jp, DeserializationContext deserializationContext) throws IOException {
        JsonNode sessionNode = jp.getCodec().readTree(jp);

        List<Integer> speakerIds = sessionNode.has("speakers") ?
                Utils.jsonArrayAsList(sessionNode.get("speakers")) : Collections.emptyList();

        return Session.builder()
                .id(sessionNode.get("id").asInt())
                .title(sessionNode.get("title").asText())
                .capacity(sessionNode.has("capacity")?sessionNode.get("capacity").asInt():0)
                .description(sessionNode.has("description")?sessionNode.get("description").asText():"")
                .service(sessionNode.has("service") ? true : false)
                .location(sessionNode.has("location")?sessionNode.get("location").asText():"")
                .sessionType(sessionNode.has("type") ? Session.SessionType.getSessionTypeById(sessionNode.get("type").asInt()) : Session.SessionType.SERVICE)
                .prerequisites(sessionNode.has("prereq") ?
                        jp.getCodec().readValue(sessionNode.get("prereq").traverse(), Session.PreReq.class)
                        : null)
                .speakers(this.speakers
                        .entrySet().stream()
                        .filter(e -> speakerIds.contains(e.getKey()))
                        .map(Map.Entry::getValue)
                        .collect(Collectors.toList()))
                .build();
    }
}

class TimeslotDeserializer extends StdDeserializer<ScheduleDay.Timeslot> {
    private final Map<Integer, Session> sessions;

    public TimeslotDeserializer(Map<Integer, Session> sessions) {
        super(ScheduleDay.Timeslot.class);
        this.sessions = sessions;
    }

    @Override
    public ScheduleDay.Timeslot deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonNode timeslotNode = p.getCodec().readTree(p);

        List<Integer> workshopIds = timeslotNode.has("workshopsIds")?
                Utils.jsonArrayAsList(timeslotNode.get("workshopsIds")):Collections.emptyList();

        List<Integer> deepdivesIds = timeslotNode.has("sessionIds")?
                Utils.jsonArrayAsList(timeslotNode.get("sessionIds")):Collections.emptyList();

        return ScheduleDay.Timeslot.builder()
                .startTime(timeslotNode.get("startTime").asText())
                .endTime(timeslotNode.get("endTime").asText())
                .workshops(this.sessions
                        .entrySet().stream()
                        .filter(e -> workshopIds.contains(e.getKey()))
                        .map(Map.Entry::getValue)
                        .collect(Collectors.toList()))
                .deepdives(this.sessions
                        .entrySet().stream()
                        .filter(e -> deepdivesIds.contains(e.getKey()))
                        .map(Map.Entry::getValue)
                        .collect(Collectors.toList()))
                .build();
    }
}

class Utils {
    public static List<Integer> jsonArrayAsList(JsonNode node) {
        Iterable<JsonNode> i = () -> node.elements();
        return StreamSupport.stream(i.spliterator(), false)
                .map(JsonNode::asInt)
                .collect(Collectors.toList());
    }
}