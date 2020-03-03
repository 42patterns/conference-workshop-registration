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
        String values = "Filip\t8f041d815837c8ab5207734b117a4f47c6853d80\n" +
                "Przemysław\t5f3c8e51f4ebdd43d32dd4bc697b6cb0a293b580\n" +
                "Małgorzata\td31556aadef994c3b48157a9dbb45e097364ccf3" +
                "\n" +
                "\n" +
                "test3\ttest3-hash";

        UserDataParser p = new UserDataParser();
        List<User> parse = p.parse(values);
        assertThat(parse, not(empty()));
        assertThat(parse, hasSize(5));
        assertThat(parse, hasItems(
                new User("Filip", "8f041d815837c8ab5207734b117a4f47c6853d80"),
                new User("Przemysław", "5f3c8e51f4ebdd43d32dd4bc697b6cb0a293b580"),
                new User("test3", "test3-hash")
        ));
    }
}