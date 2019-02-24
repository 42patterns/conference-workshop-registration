package patterns42.workshops;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

public class UserDataParser {

    private final static Logger LOG = LoggerFactory.getLogger(UserDataParser.class);
    private final static String TEST_HASH_VALUE = "test-hash-123";
    public final static Hash TEST_HASH = Hash.of(TEST_HASH_VALUE);
    public final static Username TEST_USERNAME = Username.of("Testowy Użyszkodnik");

    private final BufferedReader bufferedReader;

    public UserDataParser(BufferedReader bufferedReader) {
        this.bufferedReader = bufferedReader;
    }

    public Map<Hash, Username> parse() {

        Map<Hash, Username> collect = bufferedReader.lines()
                .map(String::strip)
                .filter(not(String::isBlank))
                .map(l -> l.split("\t"))
                .map(a -> new AbstractMap.SimpleEntry<>(Hash.of(a[1]), Username.of(a[0])))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        collect.put(TEST_HASH, TEST_USERNAME);

        LOG.info("Loaded {} users and particiants (including test account {}, {})",
                collect.size(), TEST_HASH, TEST_USERNAME);
        return collect;
    }

    @Value @RequiredArgsConstructor(staticName = "of")
    public static class Hash {
        final String hash;
    }

    @Value @RequiredArgsConstructor(staticName = "of")
    public static class Username {
        final String username;
    }
}
