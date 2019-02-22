package patterns42.workshops.agenda;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
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
import patterns42.workshops.agenda.model.Schedule;
import patterns42.workshops.agenda.model.ScheduleDay;
import patterns42.workshops.agenda.model.Session;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class ScheduleParser {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduleParser.class);
    private static final String LOCAL_SCHEDULE = "";
    private final URL path;

    public ScheduleParser(Optional<String> maybeAgendaUrl) throws MalformedURLException {
        var url = maybeAgendaUrl.
                orElse("http://test.segfault.events/sites/gdansk2019/agenda/index.yaml");
        this.path = new URL(url);
    }

    public ScheduleParser(URL rootLocation) {
        this.path = rootLocation;
    }

    public Schedule schedule() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(ScheduleDay.class, new ScheduleDayDeserializer());

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                .registerModule(new JavaTimeModule())
                .registerModule(module)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        try {
            JsonNode days = mapper.readTree(getScheduleDataFromYml())
                    .at("/agenda");
            ScheduleDay[] scheduleDays = mapper.treeToValue(days, ScheduleDay[].class);
            return new Schedule(scheduleDays);
        } catch (IOException e) {
            LOG.warn("Error parsing Schedule {}", path, e);
            throw new RuntimeException(e);
        }
    }

    private InputStream getScheduleDataFromYml() throws IOException {
        try {
            LOG.info("Opening agenda from {}", path);
            return path.openStream();
        } catch (UnknownHostException e) {
            LOG.warn("Error resolving {}. Loading local file", path);
            return getClass().getResourceAsStream("/session-data/" + LOCAL_SCHEDULE);
        }
    }
}

class ScheduleDayDeserializer extends StdDeserializer<ScheduleDay> {

    public ScheduleDayDeserializer() {
        super(ScheduleDay.class);
    }

    @Override
    public ScheduleDay deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        Map<String, List<Session>> sessions = p.readValueAs(new TypeReference<Map<String, List<Session>>>() {});
        return new ScheduleDay(sessions);
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