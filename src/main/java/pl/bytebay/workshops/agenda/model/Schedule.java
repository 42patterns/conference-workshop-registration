package pl.bytebay.workshops.agenda.model;

import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


@Getter
public class Schedule  {

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
                        .flatMap(t -> t.getWorkshops().stream()
                )).collect(Collectors.toMap(Session::getId, Function.identity()));
    }

}
