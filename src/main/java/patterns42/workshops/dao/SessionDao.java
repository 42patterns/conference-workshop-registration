package patterns42.workshops.dao;

import lombok.Builder;
import lombok.Value;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlScript;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

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

    @Value @Builder
    class SessionDTO {
        final Integer sessionId;
        final String title;
    }
}
