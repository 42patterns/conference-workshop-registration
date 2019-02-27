package patterns42.workshops;

import org.junit.Test;
import patterns42.workshops.dao.UsersDao.User;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.List;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static org.junit.Assert.assertThat;

public class UserDataParserTest {

    @Test
    public void should_parse_users_hash() {
        String values = "test\ttest-hash\n" +
                "test2\ttest2-hash\n" +
                "\n" +
                "\n" +
                "test3\ttest3-hash";

        UserDataParser p = new UserDataParser();
        List<User> parse = p.parse(values);
        assertThat(parse, not(empty()));
        assertThat(parse, hasSize(4));
        assertThat(parse, hasItems(
                new User("test", "test-hash"),
                new User("test2", "test2-hash"),
                new User("test3", "test3-hash")
        ));
    }
}