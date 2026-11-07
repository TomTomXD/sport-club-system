package cz.jpmad.springprojecttemplate;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** V5 extends a populated V4 without rewriting identities, relationships or history. */
@Testcontainers
class MatchFormatMigrationTests {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void v4DataAndMigrationChecksumsSurviveAndNewConfigurationIsConstrained() throws Exception {
        migrate("4");
        UUID admin = UUID.randomUUID(), person = UUID.randomUUID(), child = UUID.randomUUID();
        UUID player = UUID.randomUUID(), sport = UUID.randomUUID();
        try (var c = connect()) {
            execute(c, "INSERT INTO persons(id,first_name,last_name,birth_date) VALUES (?,'Test','Admin',DATE '1980-01-01'),(?,'Test','Child',DATE '2012-01-01')", person, child);
            execute(c, "INSERT INTO users(id,person_id,username,password_hash,role,account_type,enabled,created_at,updated_at) VALUES (?,?,'upgrade-admin',?,'ADMIN','STANDARD',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    admin, person, new BCryptPasswordEncoder().encode("migration-test-password"));
            execute(c, "UPDATE club SET setup_completed_by=?,setup_completed_at=CURRENT_TIMESTAMP WHERE id=1", admin);
            execute(c, "INSERT INTO player_profiles(id,person_id) VALUES (?,?)", player, child);
            execute(c, "INSERT INTO guardian_links(guardian_person_id,player_id,granted_by,granted_at) VALUES (?,?,?,CURRENT_TIMESTAMP)", person, player, admin);
            execute(c, "INSERT INTO sports(id,code,name) VALUES (?,'UPGRADE_SPORT','Test sport')", sport);
            Map<String, String> before = snapshot(c);
            String checksums = value(c, "SELECT string_agg(version || ':' || checksum, ',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success");

            migrate("5");

            var after = snapshot(c);
            assertThat(after).containsAllEntriesOf(before);
            assertThat(after).hasSize(before.size() + 1).containsKey("match_formats");
            assertThat(value(c, "SELECT string_agg(version || ':' || checksum, ',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success AND version <> '5'"))
                    .isEqualTo(checksums);
            UUID format = UUID.randomUUID();
            execute(c, "INSERT INTO match_formats(id,sport_id,name,score_mode,allow_draw,period_label,regulation_period_count) VALUES (?,?,'Test format','SUM_SCORE',TRUE,'Period',2)", format, sport);
            assertThat(value(c, "SELECT version::text FROM match_formats WHERE id=?", format)).isEqualTo("0");
            assertConstraint(c, "UPDATE match_formats SET score_mode='UNKNOWN' WHERE id=?", format);
            assertConstraint(c, "UPDATE match_formats SET friendly_forfeit_winner_score=0 WHERE id=?", format);
            assertConstraint(c, "UPDATE match_formats SET shootout_winner_bonus=1 WHERE id=?", format);
            assertConstraint(c, "UPDATE match_formats SET sport_id=? WHERE id=?", UUID.randomUUID(), format);
            execute(c, "UPDATE match_formats SET locked_at=CURRENT_TIMESTAMP WHERE id=?", format);
            execute(c, "UPDATE match_formats SET name='Renamed',version=version+1 WHERE id=?", format);
            assertConstraint(c, "UPDATE match_formats SET locked_at=NULL WHERE id=?", format);
            assertConstraint(c, "UPDATE match_formats SET regulation_period_count=4 WHERE id=?", format);
            assertConstraint(c, "DELETE FROM match_formats WHERE id=?", format);
        }
        // Validate checksums and demonstrate that a second startup has no pending DDL.
        assertThat(migrate("5").migrationsExecuted).isZero();
    }

    private Map<String, String> snapshot(Connection c) throws Exception {
        var data = new LinkedHashMap<String, String>();
        try (var s = c.createStatement(); var r = s.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename <> 'flyway_schema_history' ORDER BY tablename")) {
            while (r.next()) {
                String table = r.getString(1);
                data.put(table, value(c, "SELECT COALESCE(jsonb_agg(row ORDER BY row::text), '[]'::jsonb)::text FROM (SELECT to_jsonb(t) AS row FROM \"" + table + "\" t) rows"));
            }
        }
        return data;
    }

    private void assertConstraint(Connection c, String sql, Object... args) {
        assertThatThrownBy(() -> execute(c, sql, args)).isInstanceOf(SQLException.class)
                .extracting(e -> ((SQLException) e).getSQLState()).isEqualTo("23514");
    }

    private void execute(Connection c, String sql, Object... args) throws Exception {
        try (var s = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
            s.executeUpdate();
        }
    }

    private String value(Connection c, String sql, Object... args) throws Exception {
        try (var s = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
            try (var r = s.executeQuery()) { assertThat(r.next()).isTrue(); return r.getString(1); }
        }
    }

    private org.flywaydb.core.api.output.MigrateResult migrate(String version) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").target(MigrationVersion.fromVersion(version)).load().migrate();
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
