package cz.jpmad.springprojecttemplate;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Upgrade již používaného P1 a databázová ochrana sousedních/překrývajících intervalů. */
@Testcontainers
class RosterMigrationTests {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void v2DataSurviveAndV3ProtectsRosterIntervalsAtDatabaseLevel() throws Exception {
        migrate("2");
        UUID owner = UUID.randomUUID();
        UUID person = UUID.randomUUID();
        UUID football = UUID.randomUUID();
        UUID basketball = UUID.randomUUID();
        String hash = new BCryptPasswordEncoder().encode("roster-upgrade-test-password");
        try (Connection connection = connect()) {
            execute(connection, "INSERT INTO persons(id,first_name,last_name,birth_date) VALUES (?, 'Existing', 'Owner', DATE '1990-01-01')", person);
            execute(connection, "INSERT INTO users(id,person_id,username,password_hash,role,account_type,enabled,created_at,updated_at) VALUES (?,?,'existing-owner',?,'ADMIN','STANDARD',TRUE,TIMESTAMPTZ '2025-01-01 10:00Z',TIMESTAMPTZ '2025-01-01 10:00Z')", owner, person, hash);
            execute(connection, "UPDATE club SET name='Existing club',setup_completed_at=TIMESTAMPTZ '2025-01-01 10:00Z',setup_completed_by=? WHERE id=1", owner);
            execute(connection, "INSERT INTO sports(id,code,name) VALUES (?,'FOOTBALL','Fotbal'),(?,'BASKETBALL','Basketbal')", football, basketball);
            execute(connection, "INSERT INTO audit_log(id,actor_user_id,occurred_at,action,entity_type,entity_key) VALUES (?,?,TIMESTAMPTZ '2025-01-01 10:00Z','CLUB_SETUP_COMPLETED','club','{\"id\":1}')", UUID.randomUUID(), owner);
        }
        migrate("3");
        try (Connection connection = connect(); var statement = connection.createStatement()) {
            var account = statement.executeQuery("SELECT * FROM users WHERE username='existing-owner'");
            assertThat(account.next()).isTrue();
            assertThat(account.getObject("id", UUID.class)).isEqualTo(owner);
            assertThat(account.getObject("person_id", UUID.class)).isEqualTo(person);
            assertThat(account.getString("password_hash")).isEqualTo(hash);
            assertThat(account.getString("role")).isEqualTo("ADMIN");
            assertThat(account.getString("account_type")).isEqualTo("STANDARD");
            var setup = statement.executeQuery("SELECT * FROM club");
            setup.next();
            assertThat(setup.getString("name")).isEqualTo("Existing club");
            assertThat(setup.getObject("setup_completed_by", UUID.class)).isEqualTo(owner);
            assertThat(setup.getTimestamp("setup_completed_at")).isNotNull();
            var tables = statement.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('users','persons','club','audit_log','sports','seasons','teams','team_seasons','player_profiles','guardian_links','player_team_memberships','coach_team_memberships')");
            tables.next();
            assertThat(tables.getInt(1)).isEqualTo(12);
            var unwanted = statement.executeQuery("SELECT count(*) FROM player_profiles");
            unwanted.next();
            assertThat(unwanted.getInt(1)).isZero();
            var sports = statement.executeQuery("SELECT count(*) FROM sports");
            sports.next();
            assertThat(sports.getInt(1)).isEqualTo(2);
            var audit = statement.executeQuery("SELECT count(*) FROM audit_log WHERE action='CLUB_SETUP_COMPLETED'");
            audit.next();
            assertThat(audit.getInt(1)).isEqualTo(1);
            var extension = statement.executeQuery("SELECT count(*) FROM pg_extension WHERE extname='btree_gist'");
            extension.next();
            assertThat(extension.getInt(1)).isEqualTo(1);

            UUID season = UUID.randomUUID();
            UUID teamA = UUID.randomUUID();
            UUID teamB = UUID.randomUUID();
            UUID seasonA = UUID.randomUUID();
            UUID seasonB = UUID.randomUUID();
            UUID player = UUID.randomUUID();
            execute(connection, "INSERT INTO seasons(id,name,starts_on,ends_on) VALUES (?,'2026/2027',DATE '2026-09-01',DATE '2027-07-01')", season);
            execute(connection, "INSERT INTO teams(id,sport_id,name,is_club_team) VALUES (?,?,'A',TRUE),(?,?,'B',TRUE)", teamA, football, teamB, basketball);
            execute(connection, "INSERT INTO team_seasons(id,team_id,season_id,display_name) VALUES (?,?,?,'A 2026'),(?,?,?,'B 2026')", seasonA, teamA, season, seasonB, teamB, season);
            execute(connection, "INSERT INTO player_profiles(id,person_id) VALUES (?,?)", player, person);
            membership(connection, "player_team_memberships", "player_id", seasonA, player, "2026-09-01", "2026-11-01");
            // [a,b) a [b,c) jsou sousední, ne překrývající intervaly.
            membership(connection, "player_team_memberships", "player_id", seasonA, player, "2026-11-01", null);
            assertThatThrownBy(() -> membership(connection, "player_team_memberships", "player_id", seasonA, player, "2026-10-01", "2026-12-01"))
                    .isInstanceOf(SQLException.class).extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23P01");
            // Členství v jiném týmu/sportu se překrývat smějí.
            membership(connection, "player_team_memberships", "player_id", seasonB, player, "2026-09-01", null);
            membership(connection, "coach_team_memberships", "coach_user_id", seasonA, owner, "2026-09-01", "2026-11-01");
            membership(connection, "coach_team_memberships", "coach_user_id", seasonA, owner, "2026-11-01", null);
            assertThatThrownBy(() -> membership(connection, "coach_team_memberships", "coach_user_id", seasonA, owner, "2026-10-01", "2026-12-01"))
                    .isInstanceOf(SQLException.class).extracting(error -> ((SQLException) error).getSQLState()).isEqualTo("23P01");
            membership(connection, "coach_team_memberships", "coach_user_id", seasonB, owner, "2026-09-01", null);
        }
    }

    private void membership(Connection connection, String table, String subjectColumn, UUID teamSeason, UUID subject, String from, String to) throws Exception {
        if (!table.equals("player_team_memberships") && !table.equals("coach_team_memberships")) throw new IllegalArgumentException();
        if (!subjectColumn.equals("player_id") && !subjectColumn.equals("coach_user_id")) throw new IllegalArgumentException();
        execute(connection, "INSERT INTO " + table + "(id,team_season_id," + subjectColumn + ",valid_from,valid_to) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), teamSeason, subject, LocalDate.parse(from), to == null ? null : LocalDate.parse(to));
    }

    private void execute(Connection connection, String sql, Object... parameters) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) statement.setObject(index + 1, parameters[index]);
            statement.executeUpdate();
        }
    }

    private void migrate(String target) {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target(MigrationVersion.fromVersion(target)).locations("classpath:db/migration").load().migrate();
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
