package patterns42.workshops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;
import static patterns42.workshops.dao.UsersDao.User;

public class UserDataParser {

    private final static Logger LOG = LoggerFactory.getLogger(UserDataParser.class);
    public final static User TEST_USER = new User("Testowy Użyszkodnik", "test-hash-123");

    public List<User> parse(String values) {

        List<User> collect = values.lines()
                .map(String::strip)
                .filter(not(String::isBlank))
                .map(l -> l.split("\t"))
                .map(a -> new User(a[0], a[1]))
                .collect(Collectors.toList());

        collect.add(TEST_USER);

        LOG.info("Loaded {} users and particiants (including test account {}, {})",
                collect.size(), TEST_USER);
        return collect;
    }
}
