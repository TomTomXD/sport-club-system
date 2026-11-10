package cz.jpmad.springprojecttemplate;

import cz.jpmad.springprojecttemplate.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Ověření skutečného servlet context path, filtrů, session a migrace nad čistou PostgreSQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.servlet.session.cookie.secure=false", "app.club.setup-token="})
@Testcontainers
class SpringProjectTemplateApplicationTests {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("sport_club_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort int port;
    @Autowired JsonMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @Test
    void freshDatabaseMigratesAndHibernateValidates() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_name = 'users'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void registrationLoginRefreshSessionFixationAndLogout() throws Exception {
        var browser = new Browser();
        String username = "player-" + UUID.randomUUID();
        String password = "test-password";
        assertError(browser.get("/auth/me"), 401, "UNAUTHORIZED");

        var csrfResponse = browser.get("/auth/csrf");
        assertThat(csrfResponse.statusCode()).isEqualTo(200);
        String cookie = csrfResponse.headers().firstValue("set-cookie").orElseThrow();
        assertThat(cookie).contains("Path=/api", "HttpOnly", "SameSite=Lax").doesNotContain("Secure");
        String originalId = browser.sessionId();
        var csrf = mapper.readTree(csrfResponse.body());
        assertThat(csrf.path("headerName").asText()).isEqualTo("X-CSRF-TOKEN");
        assertThat(csrf.path("parameterName").asText()).isEqualTo("_csrf");

        var registration = browser.post("/auth/register", Map.of("username", username, "password", password,
                "role", "ADMIN"), csrf);
        assertThat(registration.statusCode()).isEqualTo(201);
        assertThat(mapper.readTree(registration.body()).path("role").asText()).isEqualTo("USER");
        assertThat(registration.body()).doesNotContain("password");
        var stored = users.findByUsername(username).orElseThrow();
        assertThat(encoder.matches(password, stored.getPasswordHash())).isTrue();
        assertThat(stored.getPasswordHash()).isNotEqualTo(password);
        assertError(browser.get("/auth/me"), 401, "UNAUTHORIZED");

        var loginCsrf = browser.csrf();
        var login = browser.post("/auth/login", Map.of("username", username, "password", password), loginCsrf);
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(browser.sessionId()).isNotEqualTo(originalId);
        assertThat(mapper.readTree(browser.get("/auth/me").body()).path("username").asText()).isEqualTo(username);
        // Nový HTTP klient simuluje reload se stejným JSESSIONID.
        assertThat(browser.reloadGet("/auth/me").statusCode()).isEqualTo(200);
        // Staré ID již nedává přístup a původní CSRF token byl při loginu zrušen.
        assertError(withCookie("/auth/me", originalId), 401, "UNAUTHORIZED");
        assertError(browser.post("/auth/logout", Map.of(), loginCsrf), 403, "FORBIDDEN");
        assertError(browser.get("/actuator/metrics"), 403, "PROFILE_INCOMPLETE");

        String authenticatedId = browser.sessionId();
        var logout = browser.post("/auth/logout", Map.of(), browser.csrf());
        assertThat(logout.statusCode()).isEqualTo(200);
        assertThat(logout.headers().allValues("set-cookie"))
                .anySatisfy(value -> assertThat(value).contains("JSESSIONID=;", "Path=/api", "Expires=Thu, 01 Jan 1970"));
        assertError(browser.get("/auth/me"), 401, "UNAUTHORIZED");
        assertError(withCookie("/auth/me", authenticatedId), 401, "UNAUTHORIZED");
        // Frontend může získat token nové anonymní session pro další login.
        assertThat(browser.csrf().path("token").asText()).isNotBlank();
    }

    @Test
    void csrfValidationBadCredentialsAndDuplicateRegistrationReturnConsistentJson() throws Exception {
        var browser = new Browser();
        var account = Map.of("username", "errors-" + UUID.randomUUID(), "password", "test-password");
        assertError(browser.post("/auth/register", account, null), 403, "FORBIDDEN");
        assertError(browser.post("/auth/login", account, null), 403, "FORBIDDEN");
        assertError(browser.post("/auth/logout", Map.of(), null), 403, "FORBIDDEN");
        assertError(browser.post("/auth/login", account, browser.csrf()), 401, "UNAUTHORIZED");
        assertError(browser.post("/auth/register", Map.of("username", "x", "password", "short"), browser.csrf()),
                400, "VALIDATION_ERROR");
        assertThat(browser.post("/auth/register", account, browser.csrf()).statusCode()).isEqualTo(201);
        assertError(browser.post("/auth/register", account, browser.csrf()), 409, "CONFLICT");
    }

    @Test
    void unicodePasswordBeyondBcryptByteLimitIsRejectedWithoutLeakingOrCreatingAccount() throws Exception {
        var browser = new Browser();
        String username = "bytes-" + UUID.randomUUID();
        String password = "ř".repeat(37); // 37 znaků, ale 74 UTF-8 bajtů.
        var response = browser.post("/auth/register", Map.of("username", username, "password", password), browser.csrf());
        assertError(response, 400, "VALIDATION_ERROR");
        assertThat(response.body()).doesNotContain(password);
        assertThat(users.findByUsername(username)).isEmpty();
        assertError(browser.post("/auth/login", Map.of("username", username, "password", password), browser.csrf()),
                400, "VALIDATION_ERROR");
    }

    @Test
    void corsPreflightUsesActualApiContextAndRejectsUntrustedOrigins() throws Exception {
        var client = HttpClient.newHttpClient();
        var allowed = client.send(HttpRequest.newBuilder(uri("/auth/login"))
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-csrf-token")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(allowed.headers().firstValue("access-control-allow-origin")).contains("http://localhost:3000");
        assertThat(allowed.headers().firstValue("access-control-allow-credentials")).contains("true");
        assertThat(allowed.headers().firstValue("access-control-allow-headers").orElseThrow().toLowerCase())
                .contains("x-csrf-token");
        var rejected = client.send(HttpRequest.newBuilder(uri("/auth/login"))
                .header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertError(rejected, 403, "FORBIDDEN");
        assertThat(rejected.headers().firstValue("access-control-allow-origin")).isEmpty();
        var rejectedActual = client.send(HttpRequest.newBuilder(uri("/info/version"))
                .header("Origin", "https://untrusted.example").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertError(rejectedActual, 403, "FORBIDDEN");
        assertThat(rejectedActual.headers().firstValue("access-control-allow-origin")).isEmpty();
        var actual = client.send(HttpRequest.newBuilder(uri("/auth/csrf"))
                .header("Origin", "http://localhost:3000").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(actual.headers().firstValue("access-control-allow-origin")).contains("http://localhost:3000");
    }

    @Test
    void publicInfoVersionHealthAndOpenApiWorkWithoutAuthentication() throws Exception {
        var browser = new Browser();
        String expectedVersion;
        try (var in = getClass().getResourceAsStream("/VERSION")) {
            assertThat(in).isNotNull();
            expectedVersion = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        }
        assertThat(mapper.readTree(browser.get("/info/version").body()).path("version").asText()).isEqualTo(expectedVersion);
        for (String endpoint : new String[]{"release-notes", "terms-of-service", "privacy-policy"}) {
            var info = browser.get("/info/" + endpoint);
            assertThat(info.statusCode()).isEqualTo(200);
            assertThat(mapper.readTree(info.body()).path("content").asText()).contains("<");
        }
        var health = browser.get("/actuator/health");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(health.body()).size()).as(health.body()).isEqualTo(1);
        assertThat(mapper.readTree(health.body()).path("status").asText()).isEqualTo("UP");
        assertError(browser.get("/actuator/metrics"), 401, "UNAUTHORIZED");
        var openApi = browser.get("/v3/api-docs");
        assertThat(openApi.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(openApi.body()).path("info").path("version").asText()).isEqualTo(expectedVersion);
        assertThat(mapper.readTree(openApi.body()).path("paths").has("/auth/login")).isTrue();
        var noBasic = HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri("/auth/me"))
                .header("Authorization", "Basic dGVzdDp0ZXN0").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertError(noBasic, 401, "UNAUTHORIZED");
        assertThat(noBasic.headers().firstValue("www-authenticate")).isEmpty();
    }

    @Test
    void setupIsUnavailableWithoutPrivateConfigurationEvenForCompleteAccount() throws Exception {
        var browser = new Browser();
        var account = Map.of("username", "no-setup-" + UUID.randomUUID(), "password", "test-password");
        assertThat(browser.post("/auth/register", account, browser.csrf()).statusCode()).isEqualTo(201);
        assertThat(browser.post("/auth/login", account, browser.csrf()).statusCode()).isEqualTo(200);
        assertThat(browser.post("/me/person", Map.of("firstName", "Test", "lastName", "Member"), browser.csrf()).statusCode())
                .isEqualTo(201);
        var status = mapper.readTree(browser.get("/club/setup-status").body());
        assertThat(status.path("completed").asBoolean()).isFalse();
        assertThat(status.path("available").asBoolean()).isFalse();
        assertError(browser.post("/club/setup", Map.of("token", "arbitrary-test-token"), browser.csrf()),
                503, "SETUP_UNAVAILABLE");
        assertThat(mapper.readTree(browser.get("/auth/me").body()).path("role").asText()).isEqualTo("USER");
    }

    private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("content-type").orElseThrow()).contains("application/json");
        var error = mapper.readTree(response.body());
        assertThat(error.path("code").asText()).isEqualTo(code);
        assertThat(error.path("timestamp").asText()).isNotBlank();
        assertThat(error.path("message").asText()).isNotBlank();
    }

    private URI uri(String path) { return URI.create("http://localhost:" + port + "/api" + path); }

    private HttpResponse<String> withCookie(String path, String id) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri(path))
                .header("Cookie", "JSESSIONID=" + id).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private class Browser {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();

        String sessionId() {
            return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("JSESSIONID"))
                    .findFirst().orElseThrow().getValue();
        }
        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> reloadGet(String path) throws Exception {
            return HttpClient.newBuilder().cookieHandler(cookies).build().send(HttpRequest.newBuilder(uri(path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }
        JsonNode csrf() throws Exception {
            var response = get("/auth/csrf");
            assertThat(response.statusCode()).isEqualTo(200);
            return mapper.readTree(response.body());
        }
        HttpResponse<String> post(String path, Object body, JsonNode csrf) throws Exception {
            var request = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (csrf != null) request.header(csrf.path("headerName").asText(), csrf.path("token").asText());
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
