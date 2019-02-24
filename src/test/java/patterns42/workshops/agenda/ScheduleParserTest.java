package patterns42.workshops.agenda;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.collection.IsCollectionWithSize;
import org.hamcrest.text.IsEmptyString;
import org.junit.Before;
import org.junit.Test;
import patterns42.workshops.agenda.model.Schedule;
import patterns42.workshops.agenda.model.ScheduleDay;
import patterns42.workshops.agenda.model.Session;
import patterns42.workshops.agenda.model.Speaker;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.List;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.text.IsEmptyString.isEmptyString;
import static org.junit.Assert.assertThat;

public class ScheduleParserTest {

    ScheduleParser parser;

    @Before
    public void setup() throws URISyntaxException, MalformedURLException {
        parser = new ScheduleParser(
                ScheduleParserTest.class.getResource("/yml/schedule.yml").toURI().toURL()
        );
    }

    @Test
    public void should_parse_schedule() {
        Schedule schedule = parser.schedule();
        assertThat(schedule.getDays(), IsCollectionWithSize.hasSize(2));
    }

    @Test
    public void should_collect_workshops_of_day_one() throws Exception {
        Schedule schedule = parser.schedule();

        ScheduleDay dayone = schedule.getDays().get(0);
        assertThat(dayone.getAllSessions(), IsCollectionWithSize.hasSize(14));

        List<Session> sessions = dayone.getAllSessions();
        assertThat(sessions, hasItems(
                withTitle("Docker kontra developer"),
                withTitle("Podstawy uczenia maszynowego dla programistów")
        ));
    }

    @Test
    public void should_return_all_sessions() {
        Schedule schedule = parser.schedule();

        List<Session> sessions = schedule.getDays().get(0).getAllSessions();
        assertThat(sessions, IsCollectionWithSize.hasSize(14));
    }

    @Test
    public void should_return_all_speakers() {
        Schedule schedule = parser.schedule();
        List<Speaker> allSpeakers = schedule.getAllSpeakers();
        assertThat(allSpeakers, IsCollectionWithSize.hasSize(26));

        assertThat(allSpeakers.get(0).getName(), not(isEmptyString()));
        assertThat(allSpeakers.get(0).getPhoto(), not(isEmptyString()));
    }

    private Matcher<Session> withTitle(String title) {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object item) {
                final Session session = (Session) item;
                return title.equals(session.getTitle());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Session with title ").appendValue(title);
            }

        };
    }
}