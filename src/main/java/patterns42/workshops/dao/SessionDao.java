package patterns42.workshops.dao;

import lombok.Builder;
import lombok.Value;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlScript;

import java.beans.ConstructorProperties;
import java.util.List;

public interface SessionDao {

    @SqlScript("CREATE TABLE IF NOT EXISTS sessions (" +
            "id SERIAL CONSTRAINT firstkey PRIMARY KEY, " +
            "hash VARCHAR(40)," +
            "sessionid SMALLINT," +
            "title VARCHAR(255)," +
            "insert_date TIMESTAMP DEFAULT now()" +
        ")")
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

    @SqlQuery("select popularity.title, count(*) as count from (" +
                "select " +
                    "sessionid, title, rank() over (partition by hash, sessionid order by insert_date desc) as rank " +
                "from sessions where hash not in (<hashes>)" +
            ") as popularity where rank=1 group by title order by count desc")
    @RegisterConstructorMapper(PopularityRank.class)
    List<PopularityRank> sessionsPopularity(@BindList("hashes") List<String> hashes);

    @Value
    class PopularityRank {
        final String title;
        final Integer count;

        @ConstructorProperties({"title", "count"})
        public PopularityRank(String title, Integer count) {
            this.title = title;
            this.count = count;
        }
    }

    @Value @Builder
    class SessionDTO {
        final Integer sessionId;
        final String title;
    }
}
