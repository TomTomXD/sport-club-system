package cz.jpmad.springprojecttemplate;

import cz.jpmad.springprojecttemplate.service.sport.SportService;
import cz.jpmad.springprojecttemplate.api.sport.dto.SportRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.sql.Connection;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.servlet.session.cookie.secure=false", "app.club.setup-token=p1-test-installation-secret"})
@Testcontainers
class IdentityAndClubSetupTests {
    private static final String TOKEN = "p1-test-installation-secret";
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    @LocalServerPort int port;
    @Autowired JsonMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired SportService sports;

    @BeforeEach void resetDatabase() {
        jdbc.execute("TRUNCATE users, persons, club, audit_log, sports CASCADE");
        jdbc.update("INSERT INTO club (id,name) VALUES (1,'Sportovní klub')");
    }

    @Test void pendingOnboardingAndContextExposeOnlyOwnIdentity() throws Exception {
        var browser = account(false);
        var me = json(browser.get("/auth/me"));
        assertThat(me.path("role").asText()).isEqualTo("USER");
        assertThat(me.path("accountType").asText()).isEqualTo("STANDARD");
        assertThat(me.path("profileState").asText()).isEqualTo("PENDING");
        assertThat(me.get("personId").isNull()).isTrue();
        assertError(browser.get("/me/person"), 404, "NOT_FOUND");
        assertThat(json(browser.get("/me/context")).path("capabilities").isEmpty()).isTrue();
        assertError(browser.get("/club/setup-status"), 403, "PROFILE_INCOMPLETE");
        assertError(browser.post("/club/setup", Map.of("token", TOKEN)), 403, "PROFILE_INCOMPLETE");
        assertError(browser.post("/sports", Map.of("code", "FOOTBALL", "name", "Fotbal")), 403, "PROFILE_INCOMPLETE");
        var person = browser.post("/me/person", Map.of("firstName", "  Petr  ", "lastName", " Novák ", "phone", " "));
        assertThat(person.statusCode()).as(person.body()).isEqualTo(201);
        var data = json(person);
        assertThat(data.path("firstName").asText()).isEqualTo("Petr");
        assertThat(data.get("birthDate").isNull()).isTrue();
        assertThat(data.get("email").isNull()).isTrue();
        assertThat(data.get("phone").isNull()).isTrue();
        assertThat(json(browser.get("/me/person")).path("id").asText()).isEqualTo(data.path("id").asText());
        assertThat(json(browser.get("/auth/me")).path("profileState").asText()).isEqualTo("COMPLETE");
        assertError(browser.post("/me/person", personBody()), 409, "PROFILE_ALREADY_LINKED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM persons", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action IN ('PERSON_CREATED','USER_PERSON_LINKED')", Long.class)).isEqualTo(2);
        assertError(browser.get("/sports"), 403, "FORBIDDEN");
    }

    @Test void validationIsStrictSafeAndDoesNotChangeState() throws Exception {
        var browser = account(false);
        var invalid = browser.post("/me/person", Map.of("firstName", " ", "lastName", "Valid", "email", "private-invalid-secret"));
        assertError(invalid, 400, "VALIDATION_ERROR");
        assertThat(json(invalid).path("fieldErrors").isArray()).isTrue();
        assertThat(json(invalid).path("details").isTextual()).isTrue();
        assertThat(invalid.body()).doesNotContain("private-invalid-secret", "rejectedValue");
        assertError(browser.post("/me/person", Map.of("firstName", "A", "lastName", "B", "birthDate", "2999-01-01")), 400, "VALIDATION_ERROR");
        assertError(browser.post("/me/person", Map.of("firstName", "A", "lastName", "B", "userId", UUID.randomUUID().toString())), 400, "BAD_REQUEST");
        assertError(browser.send("POST", "/me/person", "{\"firstName\":\"sensitive-json-secret", browser.csrf()), 400, "BAD_REQUEST");
        assertError(browser.post("/me/person", Map.of("firstName", "A", "lastName", "B", "birthDate", "not-a-date-secret")), 400, "BAD_REQUEST");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM persons", Long.class)).isZero();
    }

    @Test void tokenClaimIsOneTimeAuditedAndOnlyGrantsActualCapabilities() throws Exception {
        var browser = account(true);
        var status = json(browser.get("/club/setup-status"));
        assertThat(status.path("completed").asBoolean()).isFalse();
        assertThat(status.path("available").asBoolean()).isTrue();
        assertThat(status.path("clubName").asText()).isEqualTo("Sportovní klub");
        var wrong = browser.post("/club/setup", Map.of("token", "private-wrong-token"));
        assertError(wrong, 403, "FORBIDDEN");
        assertThat(wrong.body()).doesNotContain("private-wrong-token");
        var claim = browser.post("/club/setup", Map.of("token", TOKEN));
        assertThat(claim.statusCode()).as(claim.body()).isEqualTo(200);
        assertThat(json(claim).path("role").asText()).isEqualTo("ADMIN");
        assertThat(json(browser.get("/auth/me")).path("role").asText()).isEqualTo("ADMIN");
        assertThat(json(browser.get("/me/context")).path("capabilities").toString()).isEqualTo("[\"MANAGE_CLUB\",\"MANAGE_SPORTS\",\"MANAGE_SEASONS\",\"MANAGE_TEAMS\",\"MANAGE_PLAYERS\",\"MANAGE_MEMBERSHIPS\",\"MANAGE_ACCOUNTS\",\"MANAGE_GUARDIANS\"]");
        assertThat(json(browser.get("/club/setup-status")).path("available").asBoolean()).isFalse();
        assertError(browser.post("/club/setup", Map.of("token", TOKEN)), 409, "SETUP_ALREADY_COMPLETED");
        var second = account(true);
        assertError(second.post("/club/setup", Map.of("token", TOKEN)), 409, "SETUP_ALREADY_COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='CLUB_SETUP_COMPLETED'", Long.class)).isEqualTo(1);
        var audit = jdbc.queryForList("SELECT * FROM audit_log").toString();
        assertThat(audit).doesNotContain(TOKEN, "private-wrong-token", "test-password", "Novák");
    }

    @Test void simultaneousSetupClaimsHaveExactlyOneWinner() throws Exception {
        var a = account(true); var b = account(true);
        List<HttpResponse<String>> responses = race(() -> a.post("/club/setup", Map.of("token", TOKEN)),
                () -> b.post("/club/setup", Map.of("token", TOKEN)));
        assertThat(responses.stream().map(HttpResponse::statusCode)).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE role='ADMIN'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='CLUB_SETUP_COMPLETED'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT setup_completed_by FROM club", UUID.class)).isNotNull();
    }

    @Test void simultaneousOnboardingDoesNotCreateOrphanPerson() throws Exception {
        var browser = account(false);
        var csrf = browser.csrf();
        List<HttpResponse<String>> responses = race(() -> browser.send("POST", "/me/person", mapper.writeValueAsString(personBody()), csrf),
                () -> browser.send("POST", "/me/person", mapper.writeValueAsString(personBody()), csrf));
        var statuses=responses.stream().map(HttpResponse::statusCode).toList();
        assertThat(statuses).contains(201).allMatch(status->status==201 || status==409 || status==401);
        assertThat(statuses.stream().filter(status->status==201).count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM persons", Long.class)).isEqualTo(1);
    }

    @Test void sportsWorkForTwoConfiguredSportsAndKeepInactiveHistory() throws Exception {
        var browser = admin();
        var football = browser.post("/sports", Map.of("code", "FOOTBALL", "name", "Fotbal"));
        assertThat(football.statusCode()).as(football.body()).isEqualTo(201);
        var basketball = browser.post("/sports", Map.of("code", "BASKETBALL", "name", "Basketbal"));
        assertThat(basketball.statusCode()).isEqualTo(201);
        String id = json(football).path("id").asText();
        var page = json(browser.get("/sports?page=0&size=1"));
        assertThat(page.path("totalElements").isTextual()).isTrue();
        assertThat(page.path("totalElements").asText()).isEqualTo("2");
        assertThat(page.path("totalPages").asInt()).isEqualTo(2);
        assertThat(page.path("items").get(0).path("code").asText()).isEqualTo("BASKETBALL");
        var changed = browser.patch("/sports/" + id, Map.of("name", "Kopaný míč", "active", false));
        assertThat(changed.statusCode()).isEqualTo(200);
        assertThat(json(changed).path("active").asBoolean()).isFalse();
        assertThat(json(changed).path("code").asText()).isEqualTo("FOOTBALL");
        assertError(browser.post("/sports", Map.of("code", "FOOTBALL", "name", "Duplicita")), 409, "DUPLICATE_RESOURCE");
        assertError(browser.patch("/sports/" + id, Map.of("code", "OTHER")), 400, "BAD_REQUEST");
        assertError(browser.patch("/sports/" + id, Map.of()), 400, "VALIDATION_ERROR");
        assertError(browser.post("/sports", Map.of("code", "lowercase", "name", " ")), 400, "VALIDATION_ERROR");
        assertError(browser.get("/sports?size=101"), 400, "VALIDATION_ERROR");
        assertError(browser.get("/sports?page=invalid-secret"), 400, "VALIDATION_ERROR");
        assertError(browser.patch("/sports/not-a-uuid-secret", Map.of("active", true)), 400, "VALIDATION_ERROR");
        assertError(browser.postNoCsrf("/sports", Map.of("code", "HOCKEY", "name", "Hokej")), 403, "FORBIDDEN");
        assertError(browser.send("DELETE", "/sports/" + id, "{}", browser.csrf()), 405, "METHOD_NOT_ALLOWED");
        assertError(browser.get("/missing-club-endpoint"), 404, "NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sports", Long.class)).isEqualTo(2);
        var normal = account(true);
        assertError(normal.get("/sports"), 403, "FORBIDDEN");
        assertError(normal.post("/sports", Map.of("code", "OTHER", "name", "Jiný")), 403, "FORBIDDEN");
        assertError(normal.patch("/sports/" + UUID.randomUUID(), Map.of("active", true)), 403, "FORBIDDEN");
    }

    @Test void staleRoleAndEnabledChangesImmediatelyRevokeExistingSession() throws Exception {
        var browser = admin(); UUID id = userId(browser);
        assertThat(browser.get("/actuator/metrics").statusCode()).isEqualTo(200);
        jdbc.update("UPDATE users SET role='USER' WHERE id=?", id);
        assertError(browser.get("/actuator/metrics"), 401, "UNAUTHORIZED");
        assertThat(browser.post("/auth/login",Map.of("username",browser.username,"password","test-password")).statusCode()).isEqualTo(200);
        assertError(browser.post("/sports", Map.of("code", "FOOTBALL", "name", "Fotbal")), 403, "FORBIDDEN");
        assertThat(json(browser.get("/auth/me")).path("role").asText()).isEqualTo("USER");
        jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?", id);
        assertError(browser.get("/auth/me"), 401, "UNAUTHORIZED");
        assertError(browser.post("/auth/login", Map.of("username", browser.username, "password", "test-password")), 401, "UNAUTHORIZED");
    }

    @Test void invalidChildAndDeletedAccountFailClosedAndFreshCsrfStillWorks() throws Exception {
        var browser = account(true); UUID id = userId(browser);
        UUID profile=UUID.randomUUID();
        jdbc.update("UPDATE persons SET birth_date=DATE '2012-01-01' WHERE id=(SELECT person_id FROM users WHERE id=?)",id);
        jdbc.update("INSERT INTO player_profiles(id,person_id) SELECT ?,person_id FROM users WHERE id=?",profile,id);
        jdbc.update("UPDATE users SET account_type='CHILD',enabled=FALSE WHERE id=?", id);
        assertError(browser.get("/auth/me"), 401, "UNAUTHORIZED");
        assertError(browser.post("/auth/login", Map.of("username", browser.username, "password", "test-password")), 401, "UNAUTHORIZED");
        var disabled = account(true); UUID disabledId = userId(disabled);
        var valid = account(false);
        jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?", disabledId);
        var freshToken = disabled.csrf();
        var login = disabled.send("POST", "/auth/login", mapper.writeValueAsString(Map.of("username", valid.username, "password", "test-password")), freshToken);
        assertThat(login.statusCode()).as(login.body()).isEqualTo(200);
        var deleted = account(false); UUID deletedId = userId(deleted);
        jdbc.update("DELETE FROM users WHERE id=?", deletedId);
        assertError(deleted.get("/auth/me"), 401, "UNAUTHORIZED");
    }

    @Test void pendingAdministratorCannotBypassOnboarding() throws Exception {
        var browser = account(false); UUID id = userId(browser);
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", id);
        assertThat(browser.post("/auth/login",Map.of("username",browser.username,"password","test-password")).statusCode()).isEqualTo(200);
        assertError(browser.get("/sports"), 403, "PROFILE_INCOMPLETE");
        assertError(browser.get("/actuator/metrics"), 403, "PROFILE_INCOMPLETE");
        assertThat(json(browser.get("/me/context")).path("capabilities").isEmpty()).isTrue();
        assertThat(browser.post("/me/person", personBody()).statusCode()).isEqualTo(201);
        assertThat(browser.get("/sports").statusCode()).isEqualTo(200);
    }

    @Test void serviceRechecksPermissionsAfterWaitingForClubLock() throws Exception {
        var browser = admin(); UUID id = userId(browser);
        try (Connection blocker = java.sql.DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            blocker.setAutoCommit(false);
            blocker.createStatement().executeQuery("SELECT * FROM club WHERE id=1 FOR UPDATE");
            var executor = Executors.newSingleThreadExecutor();
            try {
                var future = executor.submit(() -> sports.create(id, new SportRequest("STALE", "Stale")));
                awaitDatabaseLock();
                jdbc.update("UPDATE users SET role='USER' WHERE id=?", id);
                blocker.commit();
                assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(AccessDeniedException.class);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM sports", Long.class)).isZero();
            } finally { executor.shutdownNow(); }
        }
    }

    private void awaitDatabaseLock() throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query ILIKE '%club%'", Integer.class);
            if (waiting != null && waiting > 0) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Service did not reach club lock");
    }
    private List<HttpResponse<String>> race(Callable<HttpResponse<String>> one, Callable<HttpResponse<String>> two) throws Exception {
        var executor = Executors.newFixedThreadPool(2); var gate = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> { gate.await(); return one.call(); });
            var second = executor.submit(() -> { gate.await(); return two.call(); });
            gate.countDown(); return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }
    private Map<String,String> personBody() { return Map.of("firstName", "Petr", "lastName", "Novák"); }
    private Browser account(boolean complete) throws Exception {
        var browser = new Browser(); browser.username = "p1-" + UUID.randomUUID();
        var credentials = Map.of("username", browser.username, "password", "test-password");
        assertThat(browser.post("/auth/register", credentials).statusCode()).isEqualTo(201);
        assertThat(browser.post("/auth/login", credentials).statusCode()).isEqualTo(200);
        if (complete) assertThat(browser.post("/me/person", personBody()).statusCode()).isEqualTo(201);
        return browser;
    }
    private Browser admin() throws Exception {
        var browser = account(true);
        assertThat(browser.post("/club/setup", Map.of("token", TOKEN)).statusCode()).isEqualTo(200);
        return browser;
    }
    private UUID userId(Browser browser) throws Exception { return UUID.fromString(json(browser.get("/auth/me")).path("id").asText()); }
    private JsonNode json(HttpResponse<String> response) throws Exception { return mapper.readTree(response.body()); }
    private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("content-type").orElse("")).contains("application/json");
        assertThat(json(response).path("code").asText()).isEqualTo(code);
    }
    private class Browser {
        String username;
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api"+path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        JsonNode csrf() throws Exception { var response = get("/auth/csrf"); assertThat(response.statusCode()).isEqualTo(200); return json(response); }
        HttpResponse<String> post(String path, Object body) throws Exception { return send("POST", path, mapper.writeValueAsString(body), csrf()); }
        HttpResponse<String> patch(String path, Object body) throws Exception { return send("PATCH", path, mapper.writeValueAsString(body), csrf()); }
        HttpResponse<String> postNoCsrf(String path, Object body) throws Exception { return send("POST", path, mapper.writeValueAsString(body), null); }
        HttpResponse<String> send(String method, String path, String body, JsonNode csrf) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api"+path)).header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
            if (csrf != null) builder.header(csrf.path("headerName").asText(), csrf.path("token").asText());
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
