package patterns42.workshops;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.collection.IsCollectionWithSize;
import org.hamcrest.core.IsCollectionContaining;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Map;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.collection.IsCollectionWithSize.*;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.IsCollectionContaining.*;
import static org.junit.Assert.assertThat;
import static patterns42.workshops.UserDataParser.*;

public class UserDataParserTest {


    @Test
    public void should_parse_users_hash() throws Exception {
        String values = "test\ttest-hash\n" +
                "test2\ttest2-hash\n" +
                "\n" +
                "\n" +
                "test3\ttest3-hash";

        BufferedReader bufferedReader = new BufferedReader(new StringReader(values));

        UserDataParser p = new UserDataParser(bufferedReader);
        Map<Hash, Username> parse = p.parse();
        assertThat(parse.keySet(), not(empty()));
        assertThat(parse.keySet(), hasSize(4));
        assertThat(parse.values(), hasItems(
                Username.of("test"),
                Username.of("test2"),
                Username.of("test3")
        ));
    }
}