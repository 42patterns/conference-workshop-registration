package patterns42.workshops.dao;

import lombok.Value;
import org.jdbi.v3.sqlobject.config.KeyColumn;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlScript;

import java.beans.ConstructorProperties;
import java.util.Map;

public interface UsersDao {

    @SqlScript("CREATE TABLE IF NOT EXISTS users (" +
                "id SERIAL CONSTRAINT userkey PRIMARY KEY, " +
                "username VARCHAR(40)," +
                "hash VARCHAR(40)," +
                "insert_date TIMESTAMP DEFAULT now()" +
            ")")
    void createUsersTable();

    @SqlBatch("insert into " +
            "users " +
            "(username, hash) " +
            "values " +
            "(:name, :hash)")
    int[] insertUserHash(@BindBean Iterable<User> users);

    @SqlQuery("select username, hash from ( " +
                "select " +
                    "username, hash, rank() over (partition by username, hash order by insert_date desc) as rank " +
                "from users " +
            ") R where r.rank=1")
    @RegisterConstructorMapper(User.class)
    @KeyColumn("hash")
    Map<String, User> allUsers();

    @SqlQuery("select username, hash, insert_date from users " +
            "where hash=:hash order by insert_date desc " +
            "limit 1")
    @RegisterConstructorMapper(User.class)
    User getUser(@Bind("hash") String hash);

    @Value
    class User {
        final String name;
        final String hash;

        @ConstructorProperties({"username", "hash"})
        public User(String name, String hash) {
            this.name = name;
            this.hash = hash;
        }
    }
}
