package pl.bytebay.workshops.agenda.model;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ScheduleDay {
    LocalDate date;
    List<Timeslot> timeslots;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    @Builder
    public static class Timeslot {
        String startTime;
        String  endTime;
        List<Session> workshops;
    }
}
