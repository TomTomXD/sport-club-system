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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Skutečný upgrade V1 s původními daty; Spring zde předčasně nespouští V2. */
@Testcontainers
class IdentityMigrationTests {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void allFiveLegacyRolesPreserveAccountsAndExistingAdministratorOwnsSetup() throws Exception {
        String schema = schema();
        migrate(schema, "1");
        String hash = new BCryptPasswordEncoder().encode("migration-test-password");
        Instant created = Instant.parse("2025-05-01T10:00:00Z");
        Instant updated = Instant.parse("2025-05-02T10:00:00Z");
        List<String> roles = List.of("ADMIN", "USER", "COACH", "PLAYER", "PARENT");
        var expectedRoles = new HashMap<UUID, String>();
        UUID adminId = UUID.randomUUID();
        try (Connection connection = connect(schema)) {
            for (String role : roles) {
                UUID id = role.equals("ADMIN") ? adminId : UUID.randomUUID();
                expectedRoles.put(id, role);
                insert(connection, id, role, hash, created, updated);
            }
            UUID secondAdmin = UUID.randomUUID();
            expectedRoles.put(secondAdmin, "ADMIN");
            insert(connection, secondAdmin, "ADMIN", hash, created.plusSeconds(60), updated);
        }
        migrate(schema, null);
        try (Connection connection = connect(schema);
             var query = connection.createStatement()) {
            var rows = query.executeQuery("SELECT * FROM users ORDER BY username");
            int count = 0;
            var actualIds = new HashSet<UUID>();
            while (rows.next()) {
                count++;
                UUID rowId = rows.getObject("id", UUID.class);
                actualIds.add(rowId);
                String legacy = rows.getString("legacy_role");
                assertThat(legacy).isEqualTo(expectedRoles.get(rowId));
                assertThat(rows.getString("role")).isEqualTo(legacy.equals("ADMIN") ? "ADMIN" : "USER");
                assertThat(rows.getString("username")).startsWith(legacy.toLowerCase() + "-");
                assertThat(rows.getString("password_hash")).isEqualTo(hash);
                assertThat(rows.getObject("person_id")).isNull();
                assertThat(rows.getString("account_type")).isEqualTo("STANDARD");
                assertThat(rows.getBoolean("enabled")).isTrue();
                assertThat(rows.getTimestamp("updated_at").toInstant()).isEqualTo(updated);
                assertThat(rows.getTimestamp("created_at").toInstant()).isIn(created, created.plusSeconds(60));
                if (rows.getObject("id", UUID.class).equals(adminId)) {
                    assertThat(rows.getTimestamp("created_at").toInstant()).isEqualTo(created);
                }
            }
            assertThat(count).isEqualTo(6);
            assertThat(actualIds).isEqualTo(expectedRoles.keySet());
            var club = query.executeQuery("SELECT * FROM club");
            assertThat(club.next()).isTrue();
            assertThat(club.getObject("setup_completed_by", UUID.class)).isEqualTo(adminId);
            assertThat(club.getTimestamp("setup_completed_at")).isNotNull();
            var people = query.executeQuery("SELECT count(*) FROM persons");
            people.next();
            assertThat(people.getInt(1)).isZero();
            var audit = query.executeQuery("SELECT count(*) FROM audit_log WHERE action='SETUP_MIGRATED' AND actor_user_id IS NULL");
            audit.next();
            assertThat(audit.getInt(1)).isEqualTo(1);
            var history = query.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success AND version IN ('1','2')");
            history.next();
            assertThat(history.getInt(1)).isEqualTo(2);
        }
    }

    @Test
    void migrationWithoutAdministratorNeverPromotesLegacyClubRolesOrFabricatesPeople() throws Exception {
        String schema = schema();
        migrate(schema, "1");
        String hash = new BCryptPasswordEncoder().encode("migration-test-password");
        Instant time = Instant.parse("2025-05-01T10:00:00Z");
        try (Connection connection = connect(schema)) {
            for (String role : List.of("USER", "COACH", "PLAYER", "PARENT")) {
                insert(connection, UUID.randomUUID(), role, hash, time, time);
            }
        }
        migrate(schema, null);
        try (Connection connection = connect(schema);
             var query = connection.createStatement()) {
            var status = query.executeQuery("SELECT setup_completed_at, setup_completed_by FROM club");
            status.next();
            assertThat(status.getObject(1)).isNull();
            assertThat(status.getObject(2)).isNull();
            var invalid = query.executeQuery("SELECT count(*) FROM users WHERE role <> 'USER' OR person_id IS NOT NULL OR account_type <> 'STANDARD'");
            invalid.next();
            assertThat(invalid.getInt(1)).isZero();
            var tables = query.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name IN ('users','persons','club','sports','audit_log')");
            tables.next();
            assertThat(tables.getInt(1)).isEqualTo(5);
        }
    }

    private String schema() {
        return "migration_" + UUID.randomUUID().toString().replace("-", "");
    }

    private void migrate(String schema, String target) {
        var configuration = Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration");
        // Tato regrese ověřuje konkrétní přechod V1→V2, ne budoucí doménové migrace.
        configuration.target(MigrationVersion.fromVersion(target == null ? "2" : target));
        configuration.load().migrate();
    }

    private Connection connect(String schema) throws Exception {
        Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        connection.setSchema(schema);
        return connection;
    }

    private void insert(Connection connection, UUID id, String role, String hash, Instant created, Instant updated) throws Exception {
        try (var insert = connection.prepareStatement("INSERT INTO users (id,username,password_hash,role,created_at,updated_at) VALUES (?,?,?,?,?,?)")) {
            insert.setObject(1, id);
            insert.setString(2, role.toLowerCase() + "-" + id.toString().substring(0, 8));
            insert.setString(3, hash);
            insert.setString(4, role);
            insert.setTimestamp(5, Timestamp.from(created));
            insert.setTimestamp(6, Timestamp.from(updated));
            insert.executeUpdate();
        }
    }
}
