package patterns42.workshops.dao;

import lombok.Builder;
import lombok.Value;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlScript;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.Optional;

public interface SessionDao {

    @SqlScript("CREATE TABLE IF NOT EXISTS sessions (" +
            "id SERIAL CONSTRAINT firstkey PRIMARY KEY, " +
            "hash VARCHAR(40)," +
            "sessionid SMALLINT," +
            "title VARCHAR(255)," +
            "insert_date TIMESTAMP DEFAULT now()" +
        ");")
    void createTable();

    @SqlBatch("insert into " +
            "sessions " +
                "(hash, sessionid, title) " +
            "values " +
                "(:hash, :sessionId, :title)")
    int[] insertSessions(@Bind("hash") String hash, @BindBean Iterable<SessionDTO> sessions);

    @SqlQuery("select ranked.title from (" +
                "select " +
                    "title, hash, rank() over (partition by hash, sessionid order by insert_date desc) as rank " +
                "from sessions" +
            ") as ranked where hash=:hash and rank=1")
    List<String> previousSessions(@Bind("hash") String hash);

    @Value @Builder
    class SessionDTO {
        final Integer sessionId;
        final String title;
    }
}
