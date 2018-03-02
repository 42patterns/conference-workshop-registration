package pl.bytebay.workshops.agenda;

import org.hamcrest.collection.IsCollectionWithSize;
import org.junit.Before;
import org.junit.Test;
import pl.bytebay.workshops.agenda.model.ScheduleDay;
import pl.bytebay.workshops.agenda.model.Session;
import pl.bytebay.workshops.agenda.model.Speaker;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class ScheduleParserTest {

    ScheduleParser parser;

    @Before
    public void setup() throws URISyntaxException {
        parser = new ScheduleParser(
                ScheduleParserTest.class.getResource("/yml").toURI()
        );
    }

    @Test
    public void should_parse_schedule() throws Exception {
        List<ScheduleDay> scheduleDay = parser.schedule();
        assertThat(scheduleDay, IsCollectionWithSize.hasSize(2));
        assertThat(scheduleDay.get(0).getTimeslots(), IsCollectionWithSize.hasSize(6));

    }

    @Test
    public void should_return_all_sessions() {
        Map<Integer, Session> sessions = parser.sessions(parser.speakers());
        assertThat(sessions.values(), IsCollectionWithSize.hasSize(46));
        assertThat(sessions.get(1).getTitle(), equalTo("JVM: przez dziurkę od klucza"));
        assertThat(sessions.get(1).getDescription(), notNullValue());
    }

    @Test
    public void should_return_all_speakers() {
        Map<Integer, Speaker> speakers = parser.speakers();
        assertThat(speakers.values(), IsCollectionWithSize.hasSize(52));
    }

}