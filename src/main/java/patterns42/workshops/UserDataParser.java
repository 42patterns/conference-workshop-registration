package patterns42.workshops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Collectors;

public class UserDataParser {

    public final static String TEST_HASH_VALUE = "test-hash-123";
    private final static Logger LOG = LoggerFactory.getLogger(UserDataParser.class);
    public Map<String, String> parse() {

        InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream("/userdata.csv"));
        BufferedReader bufferedReader = new BufferedReader(reader);

        Map<String, String> collect = bufferedReader.lines()
                .map(l -> l.split("\t"))
                .map(a -> new AbstractMap.SimpleEntry<>(a[1], a[0]))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        collect.put(TEST_HASH_VALUE, "Testowy Użyszkodnik");

        LOG.info("Loaded {} users and particiants (including test account)", collect.size());
        return collect;
    }
}
