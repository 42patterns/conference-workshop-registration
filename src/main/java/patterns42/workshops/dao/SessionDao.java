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
import java.time.LocalDateTime;
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
    int[] insertSessions(@Bind("hash") String hash, @BindBean Iterable<SessionDto> sessions);

    @SqlQuery("select ranked.title from (" +
                "select " +
                    "title, hash, rank() over (partition by hash, sessionid order by insert_date desc) as rank " +
                "from sessions" +
            ") as ranked where hash=:hash and rank=1")
    List<String> previousSessions(@Bind("hash") String hash);

    @SqlQuery("select popularity.title, count(*) as count from (" +
                "select " +
                    "sessionid, title, rank() over (partition by hash, sessionid order by insert_date desc) as rank " +
                "from sessions where hash not in (<exclusions>)" +
            ") as popularity where rank=1 group by title order by count desc")
    @RegisterConstructorMapper(PopularityRank.class)
    List<PopularityRank> sessionsPopularity(@BindList("exclusions") List<String> exclusions);

    @SqlQuery("select ranked.hash, title, insert_date from (" +
                "select *, rank() over (partition by hash order by insert_date desc) as rank from sessions where hash not in (<exclusions>)" +
            ") as ranked where rank=1")
    @RegisterConstructorMapper(RegistrationDto.class)
    List<RegistrationDto> allRegistrations(@BindList("exclusions") List<String> exclusions);

    @Value
    class RegistrationDto {
        final String hash;
        final String title;
        final LocalDateTime date;

        @ConstructorProperties({"hash", "title", "insert_date"})
        public RegistrationDto(String hash, String title, LocalDateTime date) {
            this.hash = hash;
            this.title = title;
            this.date = date;
        }
    }

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
    class SessionDto {
        final Integer sessionId;
        final String title;
    }
}
