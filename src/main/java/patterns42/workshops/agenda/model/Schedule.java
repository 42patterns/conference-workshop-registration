package patterns42.workshops.agenda.model;

import com.google.common.base.Functions;
import lombok.Value;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Value
public class Schedule {

    final List<ScheduleDay> days;

    public Schedule(ScheduleDay[] scheduleDays) {
        this.days = Arrays.asList(scheduleDays);
    }

    public List<Session> getAllSessions() {
        return days.stream()
                .flatMap(day -> day.getTimeslots().values().stream())
                .flatMap(Collection::stream)
                .filter(Predicate.not(Session::isService))
                .collect(Collectors.toList());
    }

    public List<Speaker> getAllSpeakers() {
        return days.stream()
                .flatMap(day -> day.getTimeslots().values().stream())
                .flatMap(Collection::stream)
                .flatMap(session -> session.getSpeakers().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public ScheduleDay getFirstDay() {
        return days.get(0);
    }
}
