package cz.jpmad.springprojecttemplate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.servlet.session.cookie.secure=false", "app.club.setup-token=p4-format-test-only"})
@Testcontainers
class MatchFormatsTests {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }
    @LocalServerPort int port;
    @Autowired JsonMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void reset() {
        jdbc.execute("TRUNCATE users,persons,club,audit_log,sports,seasons CASCADE");
        jdbc.update("INSERT INTO club(id,name) VALUES(1,'Test club')");
    }
    @Test void twoSportsAreConfiguredWithoutSportSpecificBranchesAndListsArePaged() throws Exception {
        var admin = admin();
        String football = sport(admin, "SPORT_A"), basketball = sport(admin, "SPORT_B");
        var a = create(admin, football, "A football", configuration(true, 2, false));
        var b = create(admin, basketball, "B basketball", configuration(false, 4, true));
        assertThat(a.path("version").isString()).isTrue();
        assertThat(a.path("version").asText()).isEqualTo("0");
        assertThat(b.path("configuration").path("allowOvertime").asBoolean()).isTrue();
        var page = json(admin.get("/match-formats?size=1"));
        assertThat(page.path("totalElements").asText()).isEqualTo("2");
        assertThat(page.path("totalPages").asInt()).isEqualTo(2);
        assertThat(page.path("items").get(0).path("id").asText()).isEqualTo(a.path("id").asText());
        var filtered = json(admin.get("/match-formats?sportId=" + basketball));
        assertThat(filtered.path("totalElements").asText()).isEqualTo("1");
        assertThat(filtered.path("items").get(0).path("id").asText()).isEqualTo(b.path("id").asText());
        var secondPage = json(admin.get("/match-formats?size=1&page=1"));
        assertThat(secondPage.path("items").get(0).path("id").asText()).isEqualTo(b.path("id").asText());
        var beyondEnd = json(admin.get("/match-formats?sportId=" + basketball + "&page=1"));
        assertThat(beyondEnd.path("items").isEmpty()).isTrue();
        assertThat(beyondEnd.path("totalElements").asText()).isEqualTo("1");
        var noMatches = json(admin.get("/match-formats?sportId=" + UUID.randomUUID()));
        assertThat(noMatches.path("items").isEmpty()).isTrue();
        assertThat(noMatches.path("totalElements").asText()).isEqualTo("0");
        assertThat(noMatches.path("totalPages").asInt()).isZero();
        error(admin.get("/match-formats?page=-1"), 400, "VALIDATION_ERROR");
        error(admin.get("/match-formats?size=0"), 400, "VALIDATION_ERROR");
        error(admin.get("/match-formats?size=101"), 400, "VALIDATION_ERROR");
        error(admin.get("/match-formats/" + UUID.randomUUID()), 404, "NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='MATCH_FORMAT_CREATED'", Long.class)).isEqualTo(2);
    }
    @Test void invalidAndIncompleteConfigurationsCannotBeSaved() throws Exception {
        var admin = admin(); String sport = sport(admin, "SPORT_A");
        var c = configuration(true, 2, false);
        c.put("shootoutWinnerBonus", 1);
        error(admin.post("/match-formats", request(sport, "Bad bonus", c)), 400, "VALIDATION_ERROR");
        c = configuration(true, 2, false); c.put("friendlyForfeitWinnerScore", 0);
        error(admin.post("/match-formats", request(sport, "Bad forfeit", c)), 400, "VALIDATION_ERROR");
        c = configuration(true, 2, false); c.put("regulationPeriodCount", 0);
        error(admin.post("/match-formats", request(sport, "Bad count", c)), 400, "VALIDATION_ERROR");
        c = configuration(true, 2, false); c.remove("allowDraw");
        error(admin.post("/match-formats", request(sport, "Missing flag", c)), 400, "VALIDATION_ERROR");
        c = configuration(true, 2, false); c.put("scoreMode", "WON_PARTS"); c.put("winsRequired", 3);
        error(admin.post("/match-formats", request(sport, "Invalid won parts", c)), 400, "VALIDATION_ERROR");
        c.put("regulationPeriodCount", null); c.put("allowDraw", false);
        create(admin, sport, "Won parts", c);
        error(admin.post("/match-formats", request(UUID.randomUUID().toString(), "Missing sport", c)), 404, "NOT_FOUND");
        ok(admin.patch("/sports/" + sport, Map.of("active", false)), 200);
        error(admin.post("/match-formats", request(sport, "Inactive sport", c)), 409, "STATE_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_formats", Long.class)).isEqualTo(1);
    }
    @Test void clientsCannotAssignIdentitySportOrHistoricalLockAndVersionsAreCanonical() throws Exception {
        var admin = admin(); String sport = sport(admin, "SPORT_A"), otherSport = sport(admin, "SPORT_B");
        var c = configuration(true, 2, false);
        var forgedCreate = new HashMap<>(request(sport, "Forged lock", c));
        forgedCreate.put("lockedAt", "2026-01-01T00:00:00Z");
        error(admin.post("/match-formats", forgedCreate), 400, "BAD_REQUEST");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_formats", Long.class)).isZero();
        String id = create(admin, sport, "Original", c).path("id").asText();
        for (var forbidden : Map.of("sportId", otherSport, "id", UUID.randomUUID().toString(), "lockedAt", "2026-01-01T00:00:00Z").entrySet()) {
            var body = new HashMap<>(update("0", "Forged update", c));
            body.put(forbidden.getKey(), forbidden.getValue());
            error(admin.put("/match-formats/" + id, body), 400, "BAD_REQUEST");
        }
        for (String version : List.of("-1", "01", "+1", "", "9223372036854775808")) {
            error(admin.put("/match-formats/" + id, update(version, "Invalid version", c)), 400, "VALIDATION_ERROR");
        }
        var current = json(admin.get("/match-formats/" + id));
        assertThat(current.path("sportId").asText()).isEqualTo(sport);
        assertThat(current.path("version").asText()).isEqualTo("0");
        assertThat(current.path("name").asText()).isEqualTo("Original");
        assertThat(current.path("lockedAt").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='MATCH_FORMAT_UPDATED'", Long.class)).isZero();
    }
    @Test void lockAllowsRenameButNeverChangesRulesAndLargeVersionsRemainExact() throws Exception {
        var admin = admin(); String sport = sport(admin, "SPORT_A"); var c = configuration(true, 2, false);
        var format = create(admin, sport, "Original", c); String id = format.path("id").asText();
        jdbc.update("UPDATE match_formats SET locked_at=CURRENT_TIMESTAMP,version=9007199254740993 WHERE id=?", UUID.fromString(id));
        var body = update("9007199254740993", "Renamed", c);
        var renamed = admin.put("/match-formats/" + id, body); ok(renamed, 200);
        assertThat(json(renamed).path("version").asText()).isEqualTo("9007199254740994");
        error(admin.put("/match-formats/" + id, body), 409, "OPTIMISTIC_LOCK_CONFLICT");
        c.put("allowDraw", false);
        error(admin.put("/match-formats/" + id, update("9007199254740994", "Changed rules", c)), 409, "CONFIGURATION_LOCKED");
        assertThat(json(admin.get("/match-formats/" + id)).path("name").asText()).isEqualTo("Renamed");
        assertThatThrownBy(() -> jdbc.update("UPDATE match_formats SET locked_at=NULL WHERE id=?", UUID.fromString(id)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE match_formats SET allow_draw=FALSE WHERE id=?", UUID.fromString(id)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM match_formats WHERE id=?", UUID.fromString(id)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void concurrentEditorsHaveOneWinnerAndFailedAuditRollsBackTheWrite() throws Exception {
        var admin = admin(); String sport = sport(admin, "SPORT_A"); var c = configuration(true, 2, false);
        String id = create(admin, sport, "Original", c).path("id").asText();
        String body = mapper.writeValueAsString(update("0", "Concurrent", c));
        JsonNode csrf = admin.csrf(); var gate = new CountDownLatch(1); var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> { gate.await(); return admin.send("PUT", "/match-formats/" + id, body, csrf); });
            var second = executor.submit(() -> { gate.await(); return admin.send("PUT", "/match-formats/" + id, body, csrf); });
            gate.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS).statusCode(), second.get(20, TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200, 409);
        } finally { executor.shutdownNow(); }
        assertThat(json(admin.get("/match-formats/" + id)).path("version").asText()).isEqualTo("1");
        jdbc.execute("ALTER TABLE audit_log ADD CONSTRAINT p4_reject_audit CHECK(action NOT IN ('MATCH_FORMAT_CREATED','MATCH_FORMAT_UPDATED')) NOT VALID");
        try {
            error(admin.post("/match-formats", request(sport, "Rollback create", c)), 409, "CONFLICT");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM match_formats", Long.class)).isEqualTo(1);
            error(admin.put("/match-formats/" + id, update("1", "Rollback", c)), 409, "CONFLICT");
            var current = json(admin.get("/match-formats/" + id));
            assertThat(current.path("name").asText()).isEqualTo("Concurrent");
            assertThat(current.path("version").asText()).isEqualTo("1");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='MATCH_FORMAT_CREATED'", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='MATCH_FORMAT_UPDATED'", Long.class)).isEqualTo(1);
        } finally { jdbc.execute("ALTER TABLE audit_log DROP CONSTRAINT p4_reject_audit"); }
    }
    @Test void onlyFreshCompleteStandardAdminHasAccessAndCsrfIsRequired() throws Exception {
        var admin = admin(); String sport = sport(admin, "SPORT_A"); var c = configuration(true, 2, false);
        String id = create(admin, sport, "Private config", c).path("id").asText();
        var user = account(true); var pending = account(false); var anonymous = new Browser();
        error(anonymous.get("/match-formats"), 401, "UNAUTHORIZED");
        for (String path : List.of("/match-formats", "/match-formats/" + id)) {
            error(user.get(path), 403, "FORBIDDEN");
            error(pending.get(path), 403, "PROFILE_INCOMPLETE");
        }
        error(user.post("/match-formats", request(sport, "Denied", c)), 403, "FORBIDDEN");
        error(user.put("/match-formats/" + id, update("0", "Denied", c)), 403, "FORBIDDEN");
        error(admin.send("PUT", "/match-formats/" + id, mapper.writeValueAsString(update("0", "No csrf", c)), null), 403, "FORBIDDEN");
        jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?", UUID.fromString(user.id));
        error(user.get("/match-formats"), 401, "UNAUTHORIZED");
    }
    @Test void activeChildCannotReadCountsDetailsOrWriteEvenWithValidCsrf() throws Exception {
        var admin = admin(); String sport = sport(admin, "SPORT_A"); var c = configuration(true, 2, false);
        String format = create(admin, sport, "Admin configuration", c).path("id").asText();
        var playerResponse = admin.post("/players", Map.of("person", Map.of("firstName", "Test", "lastName", "Child", "birthDate", "2012-01-01")));
        ok(playerResponse, 201);
        String player = json(playerResponse).path("id").asText();
        String person = jdbc.queryForObject("SELECT person_id FROM player_profiles WHERE id=?", UUID.class, UUID.fromString(player)).toString();
        String parent = json(admin.get("/me/person")).path("id").asText();
        var reason = Map.of("reason", "Ověřeno při testu oprávnění");
        ok(admin.put("/players/" + player + "/guardians/" + parent, reason), 200);
        var credentials = Map.of("username", "child-" + UUID.randomUUID(), "password", "test-password");
        var created = admin.post("/admin/users", Map.of("personId", person, "username", credentials.get("username"),
                "password", credentials.get("password"), "accountType", "CHILD"));
        ok(created, 201);
        ok(admin.post("/admin/users/" + json(created).path("id").asText() + "/activate", reason), 200);
        var child = new Browser(); ok(child.post("/auth/login", credentials), 200);
        ok(child.get("/auth/me"), 200);
        for (String path : List.of("/match-formats", "/match-formats?sportId=" + sport, "/match-formats/" + format)) {
            error(child.get(path), 403, "FORBIDDEN");
        }
        error(child.post("/match-formats", request(sport, "Denied", c)), 403, "FORBIDDEN");
        error(child.put("/match-formats/" + format, update("0", "Denied", c)), 403, "FORBIDDEN");
        for (String path : List.of("/competitions", "/competition-seasons", "/competition-seasons/" + UUID.randomUUID() + "/entries",
                "/competition-seasons/" + UUID.randomUUID() + "/point-rules", "/competition-seasons/" + UUID.randomUUID() + "/forfeit-rules",
                "/competition-seasons/" + UUID.randomUUID() + "/ranking-criteria")) {
            error(child.get(path), 403, "FORBIDDEN");
        }
        error(child.post("/competition-seasons", Map.of()), 403, "FORBIDDEN");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM match_formats", Long.class)).isEqualTo(1);
    }
    @Test void revokedAdminSessionCannotReadConfiguration() throws Exception {
        var admin = admin(); String sport = sport(admin, "SPORT_A");
        String id = create(admin, sport, "Private configuration", configuration(true, 2, false)).path("id").asText();
        var replacement = account(true);
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", UUID.fromString(replacement.id));
        jdbc.update("UPDATE users SET role='USER' WHERE id=?", UUID.fromString(admin.id));
        error(admin.get("/match-formats/" + id), 401, "UNAUTHORIZED");
        error(admin.get("/match-formats"), 401, "UNAUTHORIZED");
    }
    private Map<String, Object> configuration(boolean draw, int count, boolean overtime) {
        var c = new HashMap<String, Object>();
        c.put("scoreMode", "SUM_SCORE"); c.put("allowDraw", draw); c.put("periodsRequired", true);
        c.put("periodLabel", "Period"); c.put("regulationPeriodCount", count); c.put("winsRequired", null);
        c.put("allowOvertime", overtime); c.put("allowShootout", false); c.put("shootoutWinnerBonus", 0);
        c.put("friendlyForfeitLoserScore", 0); c.put("friendlyForfeitWinnerScore", 1); return c;
    }
    private Map<String, Object> request(String sport, String name, Map<String, Object> c) { return Map.of("sportId", sport, "name", name, "configuration", c); }
    private Map<String, Object> update(String version, String name, Map<String, Object> c) { return Map.of("version", version, "name", name, "configuration", c); }
    private JsonNode create(Browser b, String sport, String name, Map<String, Object> c) throws Exception { var r = b.post("/match-formats", request(sport, name, c)); ok(r, 201); return json(r); }
    private String sport(Browser b, String code) throws Exception { var r = b.post("/sports", Map.of("code", code, "name", code)); ok(r, 201); return json(r).path("id").asText(); }
    private Browser admin() throws Exception { var b = account(true); ok(b.post("/club/setup", Map.of("token", "p4-format-test-only")), 200); return b; }
    private Browser account(boolean complete) throws Exception {
        var b = new Browser(); var credentials = Map.of("username", "p4-" + UUID.randomUUID(), "password", "test-password");
        ok(b.post("/auth/register", credentials), 201); var r = b.post("/auth/login", credentials); ok(r, 200); b.id = json(r).path("id").asText();
        if (complete) ok(b.post("/me/person", Map.of("firstName", "Test", "lastName", "Account")), 201);
        return b;
    }
    private JsonNode json(HttpResponse<String> r) throws Exception { return mapper.readTree(r.body()); }
    private void ok(HttpResponse<String> r, int status) { assertThat(r.statusCode()).as(r.body()).isEqualTo(status); }
    private void error(HttpResponse<String> r, int status, String code) throws Exception { ok(r, status); assertThat(json(r).path("code").asText()).isEqualTo(code); }
    private class Browser {
        String id;
        final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        HttpResponse<String> get(String path) throws Exception { return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path)).GET().build(), HttpResponse.BodyHandlers.ofString()); }
        JsonNode csrf() throws Exception { var r = get("/auth/csrf"); ok(r, 200); return json(r); }
        HttpResponse<String> post(String path, Object body) throws Exception { return send("POST", path, mapper.writeValueAsString(body), csrf()); }
        HttpResponse<String> put(String path, Object body) throws Exception { return send("PUT", path, mapper.writeValueAsString(body), csrf()); }
        HttpResponse<String> patch(String path, Object body) throws Exception { return send("PATCH", path, mapper.writeValueAsString(body), csrf()); }
        HttpResponse<String> send(String method, String path, String body, JsonNode csrf) throws Exception {
            var r = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path)).header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
            if (csrf != null) r.header(csrf.path("headerName").asText(), csrf.path("token").asText());
            return client.send(r.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
