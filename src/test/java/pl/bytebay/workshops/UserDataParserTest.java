package pl.bytebay.workshops;

import org.junit.Test;

import java.util.Map;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.junit.Assert.assertThat;

public class UserDataParserTest {

    @Test
    public void should_parse_users_hash() throws Exception {
        UserDataParser p = new UserDataParser();
        Map<String, String> parse = p.parse();
        assertThat(parse.keySet(), not(empty()));
    }
}