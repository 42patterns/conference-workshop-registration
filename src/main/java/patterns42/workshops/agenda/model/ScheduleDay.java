package patterns42.workshops.agenda.model;

import lombok.Value;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Value
public class ScheduleDay {
    final Map<String, List<Session>> timeslots;

    public List<Session> getAllSessions() {
        return timeslots.values().stream()
                .flatMap(Collection::stream)
                .filter(Predicate.not(Session::isService))
                .collect(Collectors.toList());
    }

}
