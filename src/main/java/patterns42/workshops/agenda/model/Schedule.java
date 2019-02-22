package patterns42.workshops.agenda.model;

import lombok.Value;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Value
public class Schedule {

    final List<ScheduleDay> days;

    public Schedule(ScheduleDay[] scheduleDays) {
        this.days = Arrays.asList(scheduleDays);
    }

    public List<Speaker> getAllSpeakers() {
        return days.stream()
                .flatMap(day -> day.getTimeslots().values().stream())
                .flatMap(Collection::stream)
                .flatMap(session -> session.getSpeakers().stream())
                .distinct()
                .collect(Collectors.toList());
    }
}
