package patterns42.workshops.agenda.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Session {
    String title;

    @JsonProperty("abstract")
    String description;
    Integer seats;
    List<Speaker> speakers = Collections.emptyList();

    @JsonProperty("session")
    SessionType type;

    public static boolean isWorkshop(Session session) {
        return SessionType.WORKSHOP.equals(session.type);
    }

    public static boolean isService(Session session) {
        return SessionType.SERVICE.equals(session.type);
    }

    public enum SessionType {
        WORKSHOP("Warsztat"), PRESENTATION("Prezentacja"), SERVICE("Serwis");

        private static final Map<String, SessionType> lookup;

        static {
            lookup = Stream.of(SessionType.values())
                    .collect(Collectors.toMap(
                            SessionType::getName,
                            Function.identity()
                    ));
        }

        private final String name;
        SessionType(String workshop) {
            this.name = workshop;
        }

        public String getName() {
            return name;
        }

        @JsonCreator
        public static SessionType fromString(String value) {
            return lookup.getOrDefault(value, SERVICE);
        }
    }

}
