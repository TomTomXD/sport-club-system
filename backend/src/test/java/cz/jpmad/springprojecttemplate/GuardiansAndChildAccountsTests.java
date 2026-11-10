package cz.jpmad.springprojecttemplate;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"server.servlet.session.cookie.secure=false","app.club.setup-token=p3-test-setup-only"})
@Testcontainers
class GuardiansAndChildAccountsTests {
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword); }
    @TestConfiguration static class FixedTime { @Bean @Primary Clock p3Clock() { return Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"),ZoneOffset.UTC); } }
    @LocalServerPort int port;
    @Autowired JsonMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void reset() { jdbc.execute("TRUNCATE users,persons,club,audit_log,sports,seasons CASCADE");jdbc.update("INSERT INTO club(id,name) VALUES(1,'Sportovní klub')"); }
    private static final Map<String,String> WHY=Map.of("reason","Ověřeno správcem při testu");

    @Test void twoParentsAndTwoChildrenUseSameScopeForItemsCountsTeamsAndHistory() throws Exception {
        var a=admin();var p=account(true);var q=account(true);var outsider=account(true);
        String one=player(a,"First"),two=player(a,"Second"),other=player(a,"Foreign");
        grant(a,one,p.person());grant(a,two,p.person());grant(a,one,q.person());
        String team=team(a,"Football");membership(a,team,one);membership(a,team,two);
        var list=json(p.get("/me/children?size=1"));assertThat(list.path("totalElements").asText()).isEqualTo("2");assertThat(list.path("items").size()).isEqualTo(1);
        assertThat(json(q.get("/me/children")).path("totalElements").asText()).isEqualTo("1");
        assertThat(json(outsider.get("/me/children")).path("totalElements").asText()).isEqualTo("0");
        error(p.get("/me/children/"+other),404,"NOT_FOUND");error(a.get("/me/children/"+one),404,"NOT_FOUND");
        var detail=p.get("/me/children/"+one);ok(detail,200);assertThat(json(detail).size()).isEqualTo(4);assertThat(detail.body()).doesNotContain("birthDate","email","phone","personId");
        assertThat(json(p.get("/team-seasons")).path("totalElements").asText()).isEqualTo("1");ok(p.get("/team-seasons/"+team+"/players"),200);
        ok(p.get("/players/"+one+"/memberships"),200);error(p.get("/players/"+other),404,"NOT_FOUND");
        var context=json(p.get("/me/context"));assertThat(context.path("children").size()).isEqualTo(2);assertThat(context.path("teamContexts").size()).isEqualTo(1);
        revoke(a,one,p.person());error(p.get("/me/children/"+one),404,"NOT_FOUND");error(p.get("/players/"+one+"/memberships"),404,"NOT_FOUND");
        assertThat(json(p.get("/me/children")).path("totalElements").asText()).isEqualTo("1");
        revoke(a,two,p.person());error(p.get("/team-seasons/"+team),404,"NOT_FOUND");error(p.get("/team-seasons"),403,"FORBIDDEN");
    }

    @Test void childActivationNeedsQualifiedParentAndOldUnusedCookieNeverRevives() throws Exception {
        var a=admin();String player=player(a,"Child");String person=personOf(player);var child=createAccount(a,person,"CHILD");
        error(a.post("/admin/users/"+child.id+"/activate",WHY),409,"CHILD_ACTIVATION_CONFLICT");
        String offline=create(a,"/persons",Map.of("firstName","Offline","lastName","Guardian"));grant(a,player,offline);
        error(a.post("/admin/users/"+child.id+"/activate",WHY),409,"CHILD_ACTIVATION_CONFLICT");
        var p=account(true);var q=account(true);grant(a,player,p.person());grant(a,player,q.person());activate(a,child);
        var used=child.loginCopy();var unused=child.loginCopy();ok(used.get("/auth/me"),200);
        revoke(a,player,p.person());ok(used.get("/auth/me"),200);assertThat(enabled(child.id)).isTrue();
        revoke(a,player,q.person());assertThat(enabled(child.id)).isFalse();error(used.get("/auth/me"),401,"UNAUTHORIZED");
        grant(a,player,p.person());assertThat(enabled(child.id)).isFalse();activate(a,child);
        error(unused.get("/auth/me"),401,"UNAUTHORIZED");ok(child.loginCopy().get("/auth/me"),200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM player_profiles",Long.class)).isEqualTo(1);
    }

    @Test void disablingLastParentBlocksChildAndReenablingDoesNotActivateIt() throws Exception {
        var a=admin();var p=account(true);String player=player(a,"Dependent");grant(a,player,p.person());var child=createAccount(a,personOf(player),"CHILD");activate(a,child);var old=child.loginCopy();
        ok(a.patch("/admin/users/"+p.id+"/enabled",with("enabled",false)),200);assertThat(enabled(child.id)).isFalse();
        ok(a.patch("/admin/users/"+p.id+"/enabled",with("enabled",true)),200);assertThat(enabled(child.id)).isFalse();activate(a,child);
        error(old.get("/auth/me"),401,"UNAUTHORIZED");error(p.get("/auth/me"),401,"UNAUTHORIZED");
        ok(child.loginCopy().get("/auth/me"),200);
    }

    @Test void childCanReadOwnSportButEveryOtherMethodAndFinancialSurfaceFailsClosed() throws Exception {
        var a=admin();var p=account(true);String one=player(a,"Child"),other=player(a,"Teammate"),ts=team(a,"Basketball");membership(a,ts,one);membership(a,ts,other);
        grant(a,one,p.person());var account=createAccount(a,personOf(one),"CHILD");activate(a,account);var child=account.loginCopy();
        ok(child.get("/team-seasons/"+ts+"/players"),200);ok(child.get("/players/"+one+"/memberships"),200);error(child.get("/players/"+other),404,"NOT_FOUND");
        var context=json(child.get("/me/context"));assertThat(context.path("children").isEmpty()).isTrue();assertThat(context.path("capabilities").isEmpty()).isTrue();assertThat(context.path("ownPlayerId").asText()).isEqualTo(one);
        for(String path:List.of("/me/children","/admin/users","/persons","/players","/club/setup-status","/me/fees","/fees/"+UUID.randomUUID(),"/fees/export","/payments/"+UUID.randomUUID(),"/notifications","/notifications/count","/notifications/"+UUID.randomUUID(),"/me/children/"+one+"/fees")) error(child.get(path),403,"FORBIDDEN");
        for(String path:List.of("/me/person","/club/setup","/admin/users/"+child.id+"/role","/players/"+one+"/guardians/"+p.person(),"/notifications/read","/fees")) error(child.post(path,WHY),403,"FORBIDDEN");
        error(child.patch("/me/person",Map.of("birthDate","1980-01-01")),403,"FORBIDDEN");ok(child.get("/me/person"),200);
    }

    @Test void adminPersonLinkPreservesIdentityAndPermanentlyRevokesExistingCookie() throws Exception {
        var a=admin();var pending=account(false);String id=pending.id;
        String hash=jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,UUID.fromString(id));
        String person=create(a,"/persons",Map.of("firstName","Verified","lastName","Existing"));
        ok(a.post("/admin/users/"+id+"/person-link",with("personId",person)),200);error(pending.get("/auth/me"),401,"UNAUTHORIZED");
        assertThat(jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?",String.class,UUID.fromString(id))).isEqualTo(hash);
        assertThat(pending.loginCopy().person()).isEqualTo(person);
        error(a.post("/admin/users/"+id+"/person-link",with("personId",person)),409,"PROFILE_ALREADY_LINKED");
        var another=account(false);error(a.post("/admin/users/"+another.id+"/person-link",with("personId",person)),409,"DUPLICATE_RESOURCE");
        var response=a.get("/admin/users");ok(response,200);assertThat(response.body()).doesNotContain("password","authRevision","legacyRole",hash);
    }

    @Test void lastAdminAndConcurrentSelfDemotionsCannotLeaveClubUnmanaged() throws Exception {
        var a=admin();error(a.patch("/admin/users/"+a.id+"/role",with("role","USER")),409,"LAST_ADMIN_REQUIRED");
        error(a.patch("/admin/users/"+a.id+"/enabled",with("enabled",false)),409,"LAST_ADMIN_REQUIRED");
        var b=account(true);ok(a.patch("/admin/users/"+b.id+"/role",with("role","ADMIN")),200);b=b.loginCopy();
        var aa=a;var bb=b;var csrfA=a.csrf();var csrfB=b.csrf();var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var one=pool.submit(()->{gate.await();return aa.send("PATCH","/admin/users/"+aa.id+"/role",mapper.writeValueAsString(with("role","USER")),csrfA);});
            var two=pool.submit(()->{gate.await();return bb.send("PATCH","/admin/users/"+bb.id+"/role",mapper.writeValueAsString(with("role","USER")),csrfB);});gate.countDown();
            assertThat(List.of(one.get(15,TimeUnit.SECONDS).statusCode(),two.get(15,TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200,409);
        } finally {pool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE role='ADMIN' AND enabled AND person_id IS NOT NULL",Long.class)).isEqualTo(1);
    }

    @Test void childRoleTypeCoachAndGuardianConstraintsAreAdministrativeInvariants() throws Exception {
        var a=admin();var p=account(true);String one=player(a,"Protected");grant(a,one,p.person());var child=createAccount(a,personOf(one),"CHILD");
        error(a.patch("/admin/users/"+child.id+"/role",with("role","ADMIN")),409,"STATE_CONFLICT");error(a.patch("/admin/users/"+child.id+"/enabled",with("enabled",true)),409,"CHILD_ACTIVATION_CONFLICT");
        error(a.put("/players/"+one+"/guardians/"+personOf(one),WHY),409,"STATE_CONFLICT");
        String other=player(a,"Another");error(a.put("/players/"+other+"/guardians/"+personOf(one),WHY),409,"STATE_CONFLICT");
        String ts=team(a,"Coaching");error(a.post("/team-seasons/"+ts+"/coach-memberships",Map.of("coachUserId",child.id,"validFrom","2026-01-01")),409,"STATE_CONFLICT");
        String ownPlayer=create(a,"/players",Map.of("personId",p.person()));
        error(a.patch("/admin/users/"+p.id+"/account-type",with("accountType","CHILD")),409,"STATE_CONFLICT");revoke(a,one,p.person());
        create(a,"/team-seasons/"+ts+"/coach-memberships",Map.of("coachUserId",p.id,"validFrom","2026-01-01","validTo","2026-02-01"));
        error(a.patch("/admin/users/"+p.id+"/account-type",with("accountType","CHILD")),409,"STATE_CONFLICT");
        String noProfile=create(a,"/persons",Map.of("firstName","No","lastName","Profile"));error(a.post("/admin/users",Map.of("personId",noProfile,"username","nop-"+UUID.randomUUID(),"password","test-password","accountType","CHILD")),409,"CHILD_ACTIVATION_CONFLICT");
        ok(a.patch("/admin/users/"+child.id+"/account-type",with("accountType","STANDARD")),200);assertThat(enabled(child.id)).isFalse();
        ok(a.patch("/admin/users/"+child.id+"/enabled",with("enabled",true)),200);ok(child.loginCopy().get("/auth/me"),200);
    }

    @Test void guardianCyclesReasonValidationCsrfAndFailedAuditWritesAreControlled() throws Exception {
        var a=admin();String one=player(a,"AdultOne"),two=player(a,"AdultTwo");grant(a,two,personOf(one));
        error(a.put("/players/"+one+"/guardians/"+personOf(two),WHY),409,"STATE_CONFLICT");
        long before=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class);
        error(a.put("/players/"+one+"/guardians/"+personOf(two),Map.of()),400,"VALIDATION_ERROR");
        error(a.patch("/admin/users/"+a.id+"/role",Map.of("role","USER","reason"," ")),400,"VALIDATION_ERROR");
        error(a.send("PUT","/players/"+one+"/guardians/"+personOf(two),mapper.writeValueAsString(WHY),null),403,"FORBIDDEN");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class)).isEqualTo(before);
        var first=json(a.get("/players/"+two+"/guardians")).path("items").get(0);grant(a,two,personOf(one));
        assertThat(json(a.get("/players/"+two+"/guardians")).path("items").get(0).path("grantedAt")).isEqualTo(first.path("grantedAt"));
        String audit=jdbc.queryForList("SELECT * FROM audit_log").toString();assertThat(audit).doesNotContain("test-password","p3-test-setup-only","AdultOne","AdultTwo");
    }

    @Test void failedAuditInsertRollsBackNewAccountAndItsPasswordHash() throws Exception {
        var a=admin();String person=create(a,"/persons",Map.of("firstName","Rollback","lastName","Fixture"));String username="rollback-"+UUID.randomUUID();
        long users=jdbc.queryForObject("SELECT count(*) FROM users",Long.class);
        jdbc.execute("CREATE FUNCTION p3_test_reject_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='USER_CREATED' THEN RAISE EXCEPTION 'Test audit unavailable' USING ERRCODE='23514'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER p3_test_reject_audit BEFORE INSERT ON audit_log FOR EACH ROW EXECUTE FUNCTION p3_test_reject_audit()");
        try {error(a.post("/admin/users",Map.of("personId",person,"username",username,"password","private-test-password","accountType","STANDARD")),409,"CONFLICT");}
        finally {jdbc.execute("DROP TRIGGER p3_test_reject_audit ON audit_log");jdbc.execute("DROP FUNCTION p3_test_reject_audit()");}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users",Long.class)).isEqualTo(users);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE username=?",Long.class,username)).isZero();
    }

    private Browser admin() throws Exception { var a=account(true);ok(a.post("/club/setup",Map.of("token","p3-test-setup-only")),200);return a; }
    private Browser account(boolean person) throws Exception {
        var b=new Browser("p3-"+UUID.randomUUID());ok(b.post("/auth/register",credentials(b)),201);b.login();
        if(person) ok(b.post("/me/person",Map.of("firstName","Test","lastName","Account","birthDate","1985-01-01")),201);return b;
    }
    private Browser createAccount(Browser a,String person,String type) throws Exception {
        var b=new Browser("p3-"+UUID.randomUUID());var r=a.post("/admin/users",Map.of("personId",person,"username",b.username,"password","test-password","accountType",type));ok(r,201);b.id=json(r).path("id").asText();return b;
    }
    private String player(Browser a,String first) throws Exception { return create(a,"/players",Map.of("person",Map.of("firstName",first,"lastName","Player","birthDate","2012-01-01"))); }
    private String personOf(String player) { return jdbc.queryForObject("SELECT person_id FROM player_profiles WHERE id=?",UUID.class,UUID.fromString(player)).toString(); }
    private boolean enabled(String user) { return Boolean.TRUE.equals(jdbc.queryForObject("SELECT enabled FROM users WHERE id=?",Boolean.class,UUID.fromString(user))); }
    private void activate(Browser a,Browser child) throws Exception { ok(a.post("/admin/users/"+child.id+"/activate",WHY),200); }
    private void grant(Browser a,String player,String person) throws Exception { ok(a.put("/players/"+player+"/guardians/"+person,WHY),200); }
    private void revoke(Browser a,String player,String person) throws Exception { ok(a.post("/players/"+player+"/guardians/"+person+"/revoke",WHY),200); }
    private String team(Browser a,String name) throws Exception {
        String sport=create(a,"/sports",Map.of("code","SPORT_"+UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase(),"name",name));
        String season=create(a,"/seasons",Map.of("name",UUID.randomUUID().toString(),"startsOn","2026-01-01","endsOn","2027-01-01"));
        String team=create(a,"/teams",Map.of("sportId",sport,"name",name,"isClubTeam",true));return create(a,"/team-seasons",Map.of("teamId",team,"seasonId",season,"displayName",name));
    }
    private void membership(Browser a,String team,String player) throws Exception { create(a,"/team-seasons/"+team+"/player-memberships",Map.of("playerId",player,"validFrom","2026-01-01")); }
    private Map<String,Object> with(String field,Object value) { var body=new HashMap<String,Object>(WHY);body.put(field,value);return body; }
    private Map<String,String> credentials(Browser b) { return Map.of("username",b.username,"password","test-password"); }
    private String create(Browser a,String path,Object body) throws Exception { var r=a.post(path,body);ok(r,201);return json(r).path("id").asText(); }
    private JsonNode json(HttpResponse<String> r) throws Exception { return mapper.readTree(r.body()); }
    private void ok(HttpResponse<String> r,int status) { assertThat(r.statusCode()).as(r.body()).isEqualTo(status); }
    private void error(HttpResponse<String> r,int status,String code) throws Exception { ok(r,status);assertThat(json(r).path("code").asText()).isEqualTo(code); }
    private class Browser {
        String id;final String username;
        final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        Browser(String username) { this.username=username; }
        void login() throws Exception { var r=post("/auth/login",credentials(this));ok(r,200);id=json(r).path("id").asText(); }
        Browser loginCopy() throws Exception {var b=new Browser(username);b.login();return b;}
        String person() throws Exception { return json(get("/auth/me")).path("personId").asText(); }
        HttpResponse<String> get(String path) throws Exception {return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api"+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
        JsonNode csrf() throws Exception {var r=get("/auth/csrf");ok(r,200);return json(r);}
        HttpResponse<String> post(String path,Object body) throws Exception {return send("POST",path,mapper.writeValueAsString(body),csrf());}
        HttpResponse<String> patch(String path,Object body) throws Exception {return send("PATCH",path,mapper.writeValueAsString(body),csrf());}
        HttpResponse<String> put(String path,Object body) throws Exception {return send("PUT",path,mapper.writeValueAsString(body),csrf());}
        HttpResponse<String> send(String method,String path,String body,JsonNode csrf) throws Exception {
            var r=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api"+path)).header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body));
            if(csrf!=null)r.header(csrf.path("headerName").asText(),csrf.path("token").asText());return client.send(r.build(),HttpResponse.BodyHandlers.ofString());
        }
    }
}
