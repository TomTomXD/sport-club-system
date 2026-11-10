package cz.jpmad.springprojecttemplate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"server.servlet.session.cookie.secure=false","app.club.setup-token=p2-test-setup-only"})
@Testcontainers
class TeamsAndMembershipsTests {
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl); r.add("spring.datasource.username",postgres::getUsername); r.add("spring.datasource.password",postgres::getPassword);
    }
    @TestConfiguration static class FixedTime {
        @Bean @Primary Clock p2Clock() { return Clock.fixed(Instant.parse("2026-10-26T23:30:00Z"),ZoneOffset.UTC); }
    }
    @LocalServerPort int port;
    @Autowired JsonMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void reset() { jdbc.execute("TRUNCATE users,persons,club,audit_log,sports,seasons CASCADE"); jdbc.update("INSERT INTO club(id,name) VALUES(1,'Sportovní klub')"); }
    record Fixture(Browser admin,String season,String sportA,String sportB,String teamA,String teamB,String tsA,String tsB) { }

    @Test void soloAdminCanCoachTwoSportsAndKeepOfflinePlayerHistory() throws Exception {
        var f=fixture(); var a=f.admin();
        String player=createPlayer(a,"Offline");
        create(a,"/team-seasons/"+f.tsA()+"/coach-memberships",member("coachUserId",a.id,"2026-09-01",null));
        create(a,"/team-seasons/"+f.tsB()+"/coach-memberships",member("coachUserId",a.id,"2026-09-01",null));
        create(a,"/team-seasons/"+f.tsA()+"/player-memberships",member("playerId",player,"2026-09-01",null));
        create(a,"/team-seasons/"+f.tsB()+"/player-memberships",member("playerId",player,"2026-09-01",null));
        var context=json(a.get("/me/context")); assertThat(context.path("teamContexts").size()).isEqualTo(2);
        assertThat(context.path("ownPlayerId").isNull()).isTrue(); assertThat(context.path("capabilities").size()).isEqualTo(8);
        var roster=a.get("/team-seasons/"+f.tsA()+"/players"); assertThat(json(roster).path("totalElements").asText()).isEqualTo("1");
        assertThat(roster.body()).doesNotContain("personId","birthDate","email","phone","finance");
        assertThat(json(a.get("/players/"+player+"/memberships")).path("totalElements").asText()).isEqualTo("2");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM guardian_links",Long.class)).isZero();
        ok(a.patch("/teams/"+f.teamA(),Map.of("name","Renamed current team")),200);
        assertThat(json(a.get("/team-seasons/"+f.tsA())).path("displayName").asText()).isEqualTo("Fotbal 2026");
        assertThat(json(a.get("/team-seasons/"+f.tsA())).path("teamName").asText()).isEqualTo("Renamed current team");
        assertThat(json(a.get("/admin/coach-candidates")).path("totalElements").asText()).isEqualTo("1");
        String audit=jdbc.queryForList("SELECT * FROM audit_log").toString();
        assertThat(audit).doesNotContain("Offline","private@example.test","p2-test-setup-only");
    }

    @Test void currentCoachSeesOnlyAssignedTeamAndLosesAccessWithSameCookie() throws Exception {
        var f=fixture(); var coach=account(); var a=f.admin(); String player=createPlayer(a,"Scoped");
        String assignment=create(a,"/team-seasons/"+f.tsA()+"/coach-memberships",member("coachUserId",coach.id,"2026-09-01",null));
        create(a,"/team-seasons/"+f.tsA()+"/player-memberships",member("playerId",player,"2026-09-01",null));
        var list=json(coach.get("/team-seasons")); assertThat(list.path("totalElements").asText()).isEqualTo("1");
        assertThat(list.path("items").get(0).path("id").asText()).isEqualTo(f.tsA());
        error(coach.get("/team-seasons/"+f.tsB()),404,"NOT_FOUND");
        error(coach.get("/team-seasons/"+f.tsB()+"/players"),404,"NOT_FOUND");
        error(coach.get("/team-seasons/"+f.tsA()+"/players?on=2026-10-26"),403,"FORBIDDEN");
        var profile=coach.get("/players/"+player); ok(profile,200); assertThat(json(profile).size()).isEqualTo(3);
        assertThat(profile.body()).doesNotContain("personId","birthDate","email","phone");
        error(coach.get("/persons"),403,"FORBIDDEN"); error(coach.get("/players"),403,"FORBIDDEN");
        error(coach.get("/team-seasons/"+f.tsA()+"/player-memberships"),403,"FORBIDDEN");
        error(coach.post("/teams",Map.of("sportId",f.sportA(),"name","Escalation","isClubTeam",true)),403,"FORBIDDEN");
        ok(a.patch("/coach-memberships/"+assignment,interval("2026-09-01","2026-10-27")),200);
        error(coach.get("/team-seasons/"+f.tsA()),404,"NOT_FOUND");
        error(coach.get("/players/"+player),404,"NOT_FOUND");
        error(coach.get("/team-seasons"),403,"FORBIDDEN");
        assertThat(json(coach.get("/me/context")).path("teamContexts").isEmpty()).isTrue();
    }

    @Test void ownPlayerRetainsPersonalHistoryAfterMembershipEnds() throws Exception {
        var f=fixture(); var own=account(); var a=f.admin();
        String person=json(own.get("/auth/me")).path("personId").asText(); String player=create(a,"/players",Map.of("personId",person));
        String membership=create(a,"/team-seasons/"+f.tsA()+"/player-memberships",member("playerId",player,"2026-09-01",null));
        assertThat(json(own.get("/me/context")).path("ownPlayerId").asText()).isEqualTo(player);
        ok(own.get("/team-seasons/"+f.tsA()+"/players"),200);
        ok(a.patch("/player-memberships/"+membership,interval("2026-09-01","2026-10-27")),200);
        error(own.get("/team-seasons/"+f.tsA()),404,"NOT_FOUND");
        ok(own.get("/players/"+player),200);
        assertThat(json(own.get("/players/"+player+"/memberships")).path("totalElements").asText()).isEqualTo("1");
        var context=json(own.get("/me/context")); assertThat(context.path("ownPlayerId").asText()).isEqualTo(player); assertThat(context.path("teamContexts").isEmpty()).isTrue();
    }

    @Test void intervalBoundariesAdjacentTransferAndConcurrentOverlapAreEnforced() throws Exception {
        var f=fixture(); var a=f.admin(); String player=createPlayer(a,"Intervals");
        error(a.post("/team-seasons/"+f.tsA()+"/player-memberships",member("playerId",player,"2026-08-31",null)),400,"VALIDATION_ERROR");
        error(a.post("/team-seasons/"+f.tsA()+"/player-memberships",member("playerId",player,"2027-07-01",null)),400,"VALIDATION_ERROR");
        error(a.post("/team-seasons/"+f.tsA()+"/player-memberships",member("playerId",player,"2026-09-01","2027-07-02")),400,"VALIDATION_ERROR");
        create(a,"/team-seasons/"+f.tsA()+"/player-memberships",member("playerId",player,"2026-09-01","2026-10-27"));
        create(a,"/team-seasons/"+f.tsA()+"/player-memberships",member("playerId",player,"2026-10-27",null));
        assertThat(json(a.get("/team-seasons/"+f.tsA()+"/players?on=2026-10-27")).path("items").size()).isEqualTo(1);
        error(a.post("/team-seasons/"+f.tsA()+"/player-memberships",member("playerId",player,"2026-10-01",null)),409,"MEMBERSHIP_OVERLAP");
        create(a,"/team-seasons/"+f.tsB()+"/player-memberships",member("playerId",player,"2026-09-01","2027-07-01"));
        String other=createPlayer(a,"Concurrent"); String path="/team-seasons/"+f.tsA()+"/player-memberships";
        var csrf=a.csrf(); String body=mapper.writeValueAsString(member("playerId",other,"2026-09-01",null));
        var executor=Executors.newFixedThreadPool(2); var gate=new CountDownLatch(1);
        try {
            var one=executor.submit(()->{ gate.await(); return a.send("POST",path,body,csrf); });
            var two=executor.submit(()->{ gate.await(); return a.send("POST",path,body,csrf); }); gate.countDown();
            assertThat(List.of(one.get(15,TimeUnit.SECONDS).statusCode(),two.get(15,TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(201,409);
        } finally { executor.shutdownNow(); }
        create(a,"/team-seasons/"+f.tsA()+"/coach-memberships",member("coachUserId",a.id,"2026-09-01","2026-10-27"));
        create(a,"/team-seasons/"+f.tsA()+"/coach-memberships",member("coachUserId",a.id,"2026-10-27",null));
        error(a.post("/team-seasons/"+f.tsA()+"/coach-memberships",member("coachUserId",a.id,"2026-10-01",null)),409,"MEMBERSHIP_OVERLAP");
    }

    @Test void externalInactiveAndArchivedConfigurationsAreProtected() throws Exception {
        var f=fixture(); var a=f.admin(); String player=createPlayer(a,"Archive");
        String external=create(a,"/teams",Map.of("sportId",f.sportA(),"name","Opponent","isClubTeam",false));
        String extSeason=create(a,"/team-seasons",Map.of("teamId",external,"seasonId",f.season(),"displayName","Opponent 2026"));
        error(a.post("/team-seasons/"+extSeason+"/player-memberships",member("playerId",player,"2026-09-01",null)),409,"STATE_CONFLICT");
        error(a.post("/team-seasons/"+extSeason+"/coach-memberships",member("coachUserId",a.id,"2026-09-01",null)),409,"STATE_CONFLICT");
        assertThat(json(a.get("/team-seasons/"+extSeason+"/players")).path("totalElements").asText()).isEqualTo("0");
        error(a.patch("/teams/"+f.teamA(),Map.of("sportId",f.sportB())),409,"CONFIGURATION_LOCKED");
        error(a.patch("/teams/"+f.teamA(),Map.of("isClubTeam",false)),409,"CONFIGURATION_LOCKED");
        error(a.patch("/seasons/"+f.season(),Map.of("endsOn","2027-08-01")),409,"CONFIGURATION_LOCKED");
        ok(a.patch("/sports/"+f.sportB(),Map.of("active",false)),200);
        error(a.post("/teams",Map.of("sportId",f.sportB(),"name","Inactive","isClubTeam",true)),409,"STATE_CONFLICT");
        String membership=create(a,"/team-seasons/"+f.tsA()+"/player-memberships",member("playerId",player,"2026-09-01",null));
        ok(a.post("/seasons/"+f.season()+"/archive",Map.of()),200); ok(a.post("/seasons/"+f.season()+"/archive",Map.of()),200);
        error(a.patch("/player-memberships/"+membership,interval("2026-09-01","2026-11-01")),409,"CONFIGURATION_LOCKED");
        error(a.patch("/team-seasons/"+f.tsA(),Map.of("displayName","Overwrite")),409,"CONFIGURATION_LOCKED");
        error(a.patch("/seasons/"+f.season(),Map.of("name","Overwrite")),409,"CONFIGURATION_LOCKED");
        assertThat(json(a.get("/players/"+player+"/memberships")).path("totalElements").asText()).isEqualTo("1");
    }

    @Test void personIdentityValidationAndAtomicPlayerCreationDoNotMergeOrLeak() throws Exception {
        var f=fixture(); var a=f.admin();
        error(a.post("/players",Map.of("person",Map.of("firstName","No","lastName","Birthday"))),400,"VALIDATION_ERROR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM persons",Long.class)).isEqualTo(1);
        String person=create(a,"/persons",Map.of("firstName","Same","lastName","Player"));
        error(a.post("/players",Map.of("personId",person)),400,"VALIDATION_ERROR");
        error(a.post("/players",Map.of("personId",person,"person",Map.of("firstName","A","lastName","B","birthDate","2000-01-01"))),400,"VALIDATION_ERROR");
        ok(a.put("/persons/"+person,Map.of("firstName","Same","lastName","Player","birthDate","2000-01-01")),200);
        String player=create(a,"/players",Map.of("personId",person));
        error(a.post("/players",Map.of("personId",person)),409,"DUPLICATE_RESOURCE");
        error(a.put("/persons/"+person,Map.of("firstName","Same","lastName","Player")),400,"VALIDATION_ERROR");
        String another=createPlayer(a,"Same"); assertThat(another).isNotEqualTo(player);
        error(a.post("/players",Map.of("personId",UUID.randomUUID().toString())),404,"NOT_FOUND");
        var unknown=a.post("/teams",Map.of("sportId",f.sportA(),"name","A","isClubTeam",true,"secret","private-unknown"));
        error(unknown,400,"BAD_REQUEST"); assertThat(unknown.body()).doesNotContain("private-unknown");
        error(a.patch("/teams/"+f.teamA(),Map.of()),400,"VALIDATION_ERROR");
        String intervalId=create(a,"/team-seasons/"+f.tsA()+"/player-memberships",member("playerId",player,"2026-09-01",null));
        error(a.patch("/player-memberships/"+intervalId,Map.of("validFrom","2026-09-01")),400,"BAD_REQUEST");
        error(a.patch("/teams/"+f.teamA(),Collections.singletonMap("name",null)),400,"BAD_REQUEST");
        error(a.send("POST","/seasons",mapper.writeValueAsString(Map.of("name","No CSRF","startsOn","2026-01-01","endsOn","2027-01-01")),null),403,"FORBIDDEN");
    }

    @Test void futureCoachMembershipGrantsNoPrematureAccessAndArchivePreservesCurrentRead() throws Exception {
        var f=fixture(); var a=f.admin(); var coach=account();
        String membership=create(a,"/team-seasons/"+f.tsA()+"/coach-memberships",member("coachUserId",coach.id,"2026-10-28",null));
        error(coach.get("/team-seasons"),403,"FORBIDDEN");
        error(coach.get("/team-seasons/"+f.tsA()),404,"NOT_FOUND");
        assertThat(json(coach.get("/me/context")).path("teamContexts").isEmpty()).isTrue();
        ok(a.patch("/coach-memberships/"+membership,interval("2026-10-27",null)),200);
        ok(coach.get("/team-seasons/"+f.tsA()),200);
        ok(a.post("/seasons/"+f.season()+"/archive",Map.of()),200);
        ok(coach.get("/team-seasons/"+f.tsA()),200);
        error(a.patch("/coach-memberships/"+membership,interval("2026-10-27","2026-11-01")),409,"CONFIGURATION_LOCKED");
    }

    @Test void playerAndPersonCreationRollbackTogetherWhenAuditFails() throws Exception {
        var f=fixture(); var a=f.admin();
        long personCount=jdbc.queryForObject("SELECT count(*) FROM persons",Long.class);
        long auditCount=jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class);
        jdbc.execute("ALTER TABLE audit_log ADD CONSTRAINT p2_fixture_reject_player CHECK(action <> 'PLAYER_CREATED')");
        try {
            error(a.post("/players",Map.of("person",Map.of("firstName","Atomic","lastName","Rollback","birthDate","2000-01-01"))),409,"CONFLICT");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM persons",Long.class)).isEqualTo(personCount);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM player_profiles",Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log",Long.class)).isEqualTo(auditCount);
        } finally { jdbc.execute("ALTER TABLE audit_log DROP CONSTRAINT p2_fixture_reject_player"); }
    }

    private Fixture fixture() throws Exception {
        var a=account(); ok(a.post("/club/setup",Map.of("token","p2-test-setup-only")),200);
        String sa=create(a,"/sports",Map.of("code","FOOTBALL","name","Fotbal")),sb=create(a,"/sports",Map.of("code","BASKETBALL","name","Basketbal"));
        String season=create(a,"/seasons",Map.of("name","2026/27","startsOn","2026-09-01","endsOn","2027-07-01"));
        String ta=create(a,"/teams",Map.of("sportId",sa,"name","Football","isClubTeam",true)),tb=create(a,"/teams",Map.of("sportId",sb,"name","Basketball","isClubTeam",true));
        String tsa=create(a,"/team-seasons",Map.of("teamId",ta,"seasonId",season,"displayName","Fotbal 2026")),tsb=create(a,"/team-seasons",Map.of("teamId",tb,"seasonId",season,"displayName","Basketbal 2026"));
        return new Fixture(a,season,sa,sb,ta,tb,tsa,tsb);
    }
    private String createPlayer(Browser a,String first) throws Exception { return create(a,"/players",Map.of("person",Map.of("firstName",first,"lastName","Player","birthDate","2000-01-01","email","private@example.test","phone","private-phone"))); }
    private Map<String,Object> interval(String from,String to) { var m=new HashMap<String,Object>(); m.put("validFrom",from);m.put("validTo",to);return m; }
    private Map<String,Object> member(String field,String id,String from,String to) { var m=interval(from,to); m.put(field,id);return m; }
    private String create(Browser a,String path,Object body) throws Exception { var response=a.post(path,body);ok(response,201);return json(response).path("id").asText(); }
    private Browser account() throws Exception {
        var b=new Browser(); String username="p2-"+UUID.randomUUID(); var credentials=Map.of("username",username,"password","test-password");
        ok(b.post("/auth/register",credentials),201); var login=b.post("/auth/login",credentials);ok(login,200);b.id=json(login).path("id").asText();
        ok(b.post("/me/person",Map.of("firstName","Test","lastName","Account","birthDate","1990-01-01")),201);return b;
    }
    private JsonNode json(HttpResponse<String> r) throws Exception { return mapper.readTree(r.body()); }
    private void ok(HttpResponse<String> r,int status) { assertThat(r.statusCode()).as(r.body()).isEqualTo(status); }
    private void error(HttpResponse<String> r,int status,String code) throws Exception { ok(r,status); assertThat(json(r).path("code").asText()).isEqualTo(code); }
    private class Browser {
        String id;
        final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        HttpResponse<String> get(String path) throws Exception { return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api"+path)).GET().build(),HttpResponse.BodyHandlers.ofString()); }
        JsonNode csrf() throws Exception { var r=get("/auth/csrf");ok(r,200);return json(r); }
        HttpResponse<String> post(String path,Object body) throws Exception { return send("POST",path,mapper.writeValueAsString(body),csrf()); }
        HttpResponse<String> patch(String path,Object body) throws Exception { return send("PATCH",path,mapper.writeValueAsString(body),csrf()); }
        HttpResponse<String> put(String path,Object body) throws Exception { return send("PUT",path,mapper.writeValueAsString(body),csrf()); }
        HttpResponse<String> send(String method,String path,String body,JsonNode csrf) throws Exception {
            var r=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api"+path)).header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body));
            if(csrf!=null) r.header(csrf.path("headerName").asText(),csrf.path("token").asText());
            return client.send(r.build(),HttpResponse.BodyHandlers.ofString());
        }
    }
}
