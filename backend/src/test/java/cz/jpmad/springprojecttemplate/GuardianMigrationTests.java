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
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Upgrade používané P2, včetně legacy CHILD, který byl v P2 vždy blokovaný. */
@Testcontainers
class GuardianMigrationTests {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Test void v3IdentitiesAndRosterSurviveWhileV4RevokesOrphanChildSessionsPermanently() throws Exception {
        migrate("3");
        UUID admin=UUID.randomUUID(), parent=UUID.randomUUID(), child=UUID.randomUUID(), legacy=UUID.randomUUID();
        UUID adminPerson=UUID.randomUUID(), parentPerson=UUID.randomUUID(), childPerson=UUID.randomUUID();
        UUID player=UUID.randomUUID(), sport=UUID.randomUUID(), season=UUID.randomUUID(), team=UUID.randomUUID(), teamSeason=UUID.randomUUID(), membership=UUID.randomUUID();
        String hash=new BCryptPasswordEncoder().encode("guardian-upgrade-test-password");
        try (var c=connect()) {
            execute(c,"INSERT INTO persons(id,first_name,last_name,birth_date) VALUES (?,'Existing','Admin',DATE '1980-01-01'),(?,'Existing','Parent',DATE '1985-01-01'),(?,'Existing','Child',DATE '2012-01-01')",adminPerson,parentPerson,childPerson);
            user(c,admin,adminPerson,"existing-admin",hash,"ADMIN","STANDARD",true);
            user(c,parent,parentPerson,"existing-parent",hash,"USER","STANDARD",true);
            user(c,child,childPerson,"existing-child",hash,"USER","CHILD",false);
            // V3 had no usable child login; an old unsupported row must be quarantined safely.
            user(c,legacy,null,"unsupported-legacy-child",hash,"USER","CHILD",true);
            execute(c,"UPDATE club SET setup_completed_by=?,setup_completed_at=TIMESTAMPTZ '2026-01-01 10:00Z' WHERE id=1",admin);
            execute(c,"INSERT INTO player_profiles(id,person_id) VALUES (?,?)",player,childPerson);
            execute(c,"INSERT INTO guardian_links(guardian_person_id,player_id,granted_by,granted_at) VALUES (?,?,?,TIMESTAMPTZ '2026-01-01 10:00Z')",parentPerson,player,admin);
            execute(c,"INSERT INTO sports(id,code,name) VALUES (?,'FOOTBALL','Fotbal')",sport);
            execute(c,"INSERT INTO seasons(id,name,starts_on,ends_on) VALUES (?,'2026/2027',DATE '2026-07-01',DATE '2027-07-01')",season);
            execute(c,"INSERT INTO teams(id,sport_id,name,is_club_team) VALUES (?,?,'Existing team',TRUE)",team,sport);
            execute(c,"INSERT INTO team_seasons(id,team_id,season_id,display_name) VALUES (?,?,?,'Existing team 2026')",teamSeason,team,season);
            execute(c,"INSERT INTO player_team_memberships(id,team_season_id,player_id,valid_from) VALUES (?,?,?,DATE '2026-07-01')",membership,teamSeason,player);
        }
        migrate("4");
        try (var c=connect()) {
            assertThat(value(c,"SELECT password_hash FROM users WHERE id=?",admin)).isEqualTo(hash);
            assertThat(value(c,"SELECT person_id FROM users WHERE id=?",admin)).isEqualTo(adminPerson);
            assertThat(value(c,"SELECT role FROM users WHERE id=?",admin)).isEqualTo("ADMIN");
            assertThat(value(c,"SELECT auth_revision FROM users WHERE id=?",admin)).isEqualTo(0L);
            assertThat(value(c,"SELECT setup_completed_by FROM club WHERE id=1")).isEqualTo(admin);
            assertThat(value(c,"SELECT player_id FROM player_team_memberships WHERE id=?",membership)).isEqualTo(player);
            assertThat(value(c,"SELECT display_name FROM team_seasons WHERE id=?",teamSeason)).isEqualTo("Existing team 2026");
            assertThat(value(c,"SELECT enabled FROM users WHERE id=?",legacy)).isEqualTo(false);
            assertThat(((Number)value(c,"SELECT auth_revision FROM users WHERE id=?",legacy)).longValue()).isGreaterThan(0L);
            assertThat(value(c,"SELECT person_id FROM users WHERE id=?",legacy)).isNull();
            assertThat(value(c,"SELECT count(*) FROM persons")).isEqualTo(3L);
            assertThat(value(c,"SELECT count(*) FROM player_profiles")).isEqualTo(1L);
            assertThat(value(c,"SELECT enabled FROM users WHERE id=?",child)).isEqualTo(false);

            execute(c,"UPDATE users SET enabled=TRUE WHERE id=?",child);
            long loginRevision=revision(c,child);
            execute(c,"UPDATE guardian_links SET revoked_at=CURRENT_TIMESTAMP WHERE player_id=? AND guardian_person_id=?",player,parentPerson);
            assertThat(value(c,"SELECT enabled FROM users WHERE id=?",child)).isEqualTo(false);
            assertThat(revision(c,child)).isGreaterThan(loginRevision);
            execute(c,"UPDATE guardian_links SET revoked_at=NULL WHERE player_id=? AND guardian_person_id=?",player,parentPerson);
            assertThat(value(c,"SELECT enabled FROM users WHERE id=?",child)).isEqualTo(false);
            execute(c,"UPDATE users SET enabled=TRUE WHERE id=?",child);
            assertThat(revision(c,child)).isGreaterThan(loginRevision); // Original session cannot revive.
            long secondLogin=revision(c,child);
            execute(c,"UPDATE users SET enabled=FALSE WHERE id=?",parent);
            assertThat(value(c,"SELECT enabled FROM users WHERE id=?",child)).isEqualTo(false);
            assertThat(revision(c,child)).isGreaterThan(secondLogin);
            execute(c,"UPDATE users SET enabled=TRUE WHERE id=?",parent);
            assertThat(value(c,"SELECT enabled FROM users WHERE id=?",child)).isEqualTo(false);
            assertThat(value(c,"SELECT count(*) FROM player_team_memberships")).isEqualTo(1L);
            assertThat(value(c,"SELECT count(*) FROM guardian_links WHERE revoked_at IS NULL")).isEqualTo(1L);
            assertThatThrownBy(() -> execute(c,"INSERT INTO guardian_links(guardian_person_id,player_id,granted_by,granted_at) VALUES (?,?,?,CURRENT_TIMESTAMP)",childPerson,player,admin))
                    .isInstanceOf(SQLException.class).extracting(e -> ((SQLException)e).getSQLState()).isEqualTo("23514");
            assertThatThrownBy(() -> execute(c,"UPDATE users SET person_id=? WHERE id=?",adminPerson,child))
                    .isInstanceOf(SQLException.class).extracting(e -> ((SQLException)e).getSQLState()).isEqualTo("23514");
        }
    }
    private long revision(Connection c,UUID id) throws Exception { return ((Number)value(c,"SELECT auth_revision FROM users WHERE id=?",id)).longValue(); }
    private void user(Connection c,UUID id,UUID person,String name,String hash,String role,String type,boolean enabled) throws Exception {
        execute(c,"INSERT INTO users(id,person_id,username,password_hash,role,account_type,enabled,created_at,updated_at) VALUES (?,?,?,?,?,?,?,TIMESTAMPTZ '2026-01-01 10:00Z',TIMESTAMPTZ '2026-01-01 10:00Z')",id,person,name,hash,role,type,enabled);
    }
    private Object value(Connection c,String sql,Object... args) throws Exception {
        try (var s=c.prepareStatement(sql)) { for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]); try(var r=s.executeQuery()){assertThat(r.next()).isTrue();return r.getObject(1);} }
    }
    private void execute(Connection c,String sql,Object... args) throws Exception {
        try (var s=c.prepareStatement(sql)) { for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate(); }
    }
    private void migrate(String version) { Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).locations("classpath:db/migration").target(MigrationVersion.fromVersion(version)).load().migrate(); }
    private Connection connect() throws Exception { return DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()); }
}
