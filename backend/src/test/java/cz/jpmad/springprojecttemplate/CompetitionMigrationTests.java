package cz.jpmad.springprojecttemplate;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class CompetitionMigrationTests {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @Test void v5IdentityHistoryAndFormatRowsSurviveUpgradeToNineteenTables() throws Exception {
        migrate("5");
        UUID admin=UUID.randomUUID(),person=UUID.randomUUID(),sport=UUID.randomUUID(),format=UUID.randomUUID();
        try(var c=connect()) {
            execute(c,"INSERT INTO persons(id,first_name,last_name) VALUES (?,'Test','Admin')",person);
            execute(c,"INSERT INTO users(id,person_id,username,password_hash,role,account_type,enabled,created_at,updated_at) VALUES (?,?,'upgrade-p4',?,'ADMIN','STANDARD',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",admin,person,new BCryptPasswordEncoder().encode("migration-test-password"));
            execute(c,"UPDATE club SET setup_completed_by=?,setup_completed_at=CURRENT_TIMESTAMP WHERE id=1",admin);
            execute(c,"INSERT INTO sports(id,code,name) VALUES (?,'UPGRADE','Test sport')",sport);
            execute(c,"INSERT INTO seasons(id,name,starts_on,ends_on) VALUES (?,'Past season',DATE '2024-01-01',DATE '2025-01-01')",UUID.randomUUID());
            execute(c,"INSERT INTO match_formats(id,sport_id,name,score_mode,allow_draw,period_label,locked_at) VALUES (?,?,'Existing locked format','SUM_SCORE',TRUE,'Period',CURRENT_TIMESTAMP)",format,sport);
            var before=snapshot(c);assertThat(before).hasSize(13);
            var checksums=value(c,"SELECT string_agg(version || ':' || checksum,',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success");
            migrate("6");
            var after=snapshot(c);assertThat(after).hasSize(19).containsAllEntriesOf(before);
            assertThat(value(c,"SELECT string_agg(version || ':' || checksum,',' ORDER BY installed_rank) FROM flyway_schema_history WHERE success AND version<>'6'")).isEqualTo(checksums);
            assertThat(after.keySet()).contains("competitions","competition_seasons","competition_point_rules","competition_forfeit_rules","competition_ranking_criteria","competition_entries");
            assertThatThrownBy(()->execute(c,"UPDATE match_formats SET locked_at=NULL WHERE id=?",format)).isInstanceOf(SQLException.class);
        }
        assertThat(migrate("6").migrationsExecuted).isZero();
    }
    private Map<String,String> snapshot(Connection c) throws Exception {
        var result=new LinkedHashMap<String,String>();
        try(var s=c.createStatement();var rows=s.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename<>'flyway_schema_history' ORDER BY tablename")) {
            while(rows.next()) {
                String table=rows.getString(1);
                result.put(table,value(c,"SELECT COALESCE(jsonb_agg(row ORDER BY row::text),'[]'::jsonb)::text FROM (SELECT to_jsonb(t) row FROM \""+table+"\" t) rows"));
            }
        }
        return result;
    }
    private void execute(Connection c,String sql,Object... args) throws Exception {
        try(var s=c.prepareStatement(sql)) { for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate(); }
    }
    private String value(Connection c,String sql) throws Exception { try(var s=c.createStatement();var r=s.executeQuery(sql)){assertThat(r.next()).isTrue();return r.getString(1);} }
    private org.flywaydb.core.api.output.MigrateResult migrate(String version) {
        return Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).locations("classpath:db/migration").target(MigrationVersion.fromVersion(version)).load().migrate();
    }
    private Connection connect() throws Exception { return DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()); }
}
