package pl.bytebay.workshops.agenda.model;

import lombok.*;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Session {
    Integer id;
    String title;
    Boolean service;
    String description;
    Integer capacity;
    List<Speaker> speakers;
    PreReq prerequisites;
    SessionType sessionType;

    public enum SessionType {
        WORKSHOP("workshop"), DEEP_DIVE("deep dive"), KEYNOTE("keynote"), SERVICE("service");

        public final String name;

        SessionType(String workshop) {
            this.name = workshop;
        }

        public static SessionType getSessionTypeById(Integer id) {
            switch (id) {
                case 0:
                    return SessionType.WORKSHOP;
                case 1:
                    return SessionType.DEEP_DIVE;
                case 2:
                    return SessionType.KEYNOTE;
                default:
                    throw new IllegalStateException("Id not valid");
            }
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class PreReq {
        String title;
        List<String> list;
    }
}
