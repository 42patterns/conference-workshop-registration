package pl.bytebay.workshops.agenda.model;

import lombok.Getter;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Getter
public class Schedule {

    List<ScheduleDay> days;

    public Schedule() {
        this.days = Collections.emptyList();
    }

    public Schedule(ScheduleDay[] scheduleDays) {
        this.days = Arrays.asList(scheduleDays);
    }

    public Map<Integer, Session> getAllSessions() {
        return days.stream()
                .flatMap(day -> day.getTimeslots()
                        .stream()
                        .flatMap(t -> Stream.of(t.getWorkshops(), t.getDeepdives())
                                .flatMap(Collection::stream))
                )
                .collect(Collectors.toMap(Session::getId, Function.identity()));
    }

}
