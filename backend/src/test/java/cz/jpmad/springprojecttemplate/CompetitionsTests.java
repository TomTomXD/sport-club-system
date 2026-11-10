package cz.jpmad.springprojecttemplate;

import cz.jpmad.springprojecttemplate.api.competition.dto.CompetitionDtos.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"server.servlet.session.cookie.secure=false","app.club.setup-token=p4-competition-test-only"})
@Testcontainers
class CompetitionsTests {
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword); }
    @LocalServerPort int port;
    @Autowired JsonMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void reset() { jdbc.execute("TRUNCATE users,persons,club,audit_log,sports,seasons CASCADE");jdbc.update("INSERT INTO club(id,name) VALUES(1,'Test club')"); }
    private record Fixture(Browser admin,String sport,String season,String format,String competition,Configuration configuration) { }
    private Configuration config(boolean draw,boolean overtime) {
        var points=new ArrayList<PointRule>();
        for(var ending:overtime?List.of(Ending.REGULATION,Ending.OVERTIME):List.of(Ending.REGULATION)) {
            points.add(new PointRule(Outcome.WIN,ending,null,null,null,null,draw?3:2));
            if(draw) points.add(new PointRule(Outcome.DRAW,ending,null,null,null,null,1));
            points.add(new PointRule(Outcome.LOSS,ending,null,null,null,null,draw?0:1));
        }
        return new Configuration(MissingPolicy.REQUIRE_COMPLETE,2,true,points,List.of(
                new ForfeitRule(Decision.FORFEIT_HOME,0,draw?3:20,0,draw?3:2),new ForfeitRule(Decision.FORFEIT_AWAY,draw?3:20,0,draw?3:2,0)),
                List.of(new RankingCriterion(1,Metric.POINTS,Direction.DESC),new RankingCriterion(2,Metric.HEAD_TO_HEAD_POINTS,Direction.DESC),new RankingCriterion(3,Metric.SCORE_DIFFERENCE,Direction.DESC)));
    }
    private Fixture fixture() throws Exception {
        var a=account(true);ok(a.post("/club/setup",Map.of("token","p4-competition-test-only")),200);
        String sport=create(a,"/sports",Map.of("code","SPORT_A","name","Sport A"));
        String season=create(a,"/seasons",Map.of("name","Test season","startsOn","2026-01-01","endsOn","2027-01-01"));
        String format=create(a,"/match-formats",Map.of("sportId",sport,"name","Format A","configuration",format(true,false)));
        String competition=create(a,"/competitions",Map.of("sportId",sport,"name","Competition A"));
        return new Fixture(a,sport,season,format,competition,config(true,false));
    }
    private Map<String,Object> format(boolean draw,boolean overtime) {
        var c=new HashMap<String,Object>();c.put("scoreMode","SUM_SCORE");c.put("allowDraw",draw);c.put("periodsRequired",true);c.put("periodLabel","Period");c.put("regulationPeriodCount",overtime?4:2);
        c.put("allowOvertime",overtime);c.put("allowShootout",false);c.put("shootoutWinnerBonus",0);c.put("friendlyForfeitLoserScore",0);c.put("friendlyForfeitWinnerScore",3);return c;
    }
    private Object request(Fixture f,Configuration c) { return Map.of("competitionId",f.competition(),"seasonId",f.season(),"matchFormatId",f.format(),"configuration",c); }
    private String competitionSeason(Fixture f) throws Exception { var r=f.admin().post("/competition-seasons",request(f,f.configuration()));ok(r,201);return json(r).path("season").path("id").asText(); }
    @Test void footballAndBasketballConfigurationsArePersistedAndListCountsUseTheSameScope() throws Exception {
        var f=fixture();String first=competitionSeason(f);var a=f.admin();
        String sport=create(a,"/sports",Map.of("code","SPORT_B","name","Sport B"));
        String format=create(a,"/match-formats",Map.of("sportId",sport,"name","Format B","configuration",format(false,true)));
        String competition=create(a,"/competitions",Map.of("sportId",sport,"name","Competition B"));
        var second=a.post("/competition-seasons",Map.of("competitionId",competition,"seasonId",f.season(),"matchFormatId",format,"configuration",config(false,true)));ok(second,201);
        var detail=json(a.get("/competition-seasons/"+first));assertThat(detail.path("season").path("version").asText()).isEqualTo("0");assertThat(detail.path("configuration").path("pointRules").size()).isEqualTo(3);
        var filtered=json(a.get("/competition-seasons?sportId="+sport));assertThat(filtered.path("totalElements").asText()).isEqualTo("1");assertThat(filtered.path("items").get(0).path("competitionId").asText()).isEqualTo(competition);
        assertThat(json(a.get("/competition-seasons?competitionId="+f.competition()+"&seasonId="+f.season())).path("totalElements").asText()).isEqualTo("1");
        assertThat(json(a.get("/competition-seasons?size=1&page=1")).path("items").size()).isEqualTo(1);
        assertThat(json(a.get("/competition-seasons?sportId="+UUID.randomUUID())).path("totalElements").asText()).isEqualTo("0");
        assertThat(json(a.get("/competitions?sportId="+sport)).path("totalElements").asText()).isEqualTo("1");
        ok(a.put("/competitions/"+f.competition(),Map.of("name","Renamed")),200);
        assertThat(json(a.get("/competition-seasons/"+first)).path("season").path("competitionName").asText()).isEqualTo("Renamed");
        for(String path:List.of("point-rules","forfeit-rules","ranking-criteria")) ok(a.get("/competition-seasons/"+first+"/"+path),200);
        error(a.get("/competitions?size=101"),400,"VALIDATION_ERROR");error(a.get("/competition-seasons?page=-1"),400,"VALIDATION_ERROR");
        error(a.get("/competition-seasons/"+UUID.randomUUID()),404,"NOT_FOUND");
    }
    @Test void foreignSportSeasonAndIncompleteOrOverlappingConfigurationAreRejectedAtomically() throws Exception {
        var f=fixture();var a=f.admin();var c=f.configuration();
        var overlap=new ArrayList<>(c.pointRules());overlap.add(overlap.get(0));
        error(a.post("/competition-seasons",request(f,new Configuration(c.headToHeadMissingPolicy(),2,true,overlap,c.forfeitRules(),c.rankingCriteria()))),400,"VALIDATION_ERROR");
        var gap=c.pointRules().stream().filter(r->r.outcome()!=Outcome.DRAW).toList();
        error(a.post("/competition-seasons",request(f,new Configuration(c.headToHeadMissingPolicy(),2,true,gap,c.forfeitRules(),c.rankingCriteria()))),400,"VALIDATION_ERROR");
        String other=create(a,"/sports",Map.of("code","SPORT_B","name","Sport B"));String foreign=create(a,"/match-formats",Map.of("sportId",other,"name","Foreign","configuration",format(true,false)));
        error(a.post("/competition-seasons",Map.of("competitionId",f.competition(),"seasonId",f.season(),"matchFormatId",foreign,"configuration",c)),400,"VALIDATION_ERROR");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM competition_seasons",Long.class)).isZero();
        String id=competitionSeason(f);error(a.post("/competition-seasons",request(f,c)),409,"DUPLICATE_RESOURCE");
        assertThatThrownBy(()->jdbc.update("INSERT INTO competition_point_rules(id,competition_season_id,outcome,ending,points) VALUES (?,?,'WIN','REGULATION',1)",UUID.randomUUID(),UUID.fromString(id)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("INSERT INTO competition_seasons(id,competition_id,season_id,match_format_id) VALUES (?,?,?,?)",UUID.randomUUID(),UUID.fromString(f.competition()),UUID.fromString(f.season()),UUID.fromString(foreign)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        String own=team(a,f.sport(),f.season(),"Club",true),external=team(a,f.sport(),f.season(),"Opponent",false);
        String wrongSport=team(a,other,f.season(),"Foreign",true);
        String otherSeason=create(a,"/seasons",Map.of("name","Other season","startsOn","2027-01-01","endsOn","2028-01-01"));
        String wrongSeason=team(a,f.sport(),otherSeason,"Next",true);
        for(String bad:List.of(wrongSport,wrongSeason)) error(a.post("/competition-seasons/"+id+"/entries",Map.of("version","0","teamSeasonId",bad)),400,"VALIDATION_ERROR");
        ok(a.post("/competition-seasons/"+id+"/entries",Map.of("version","0","teamSeasonId",own)),200);
        error(a.post("/competition-seasons/"+id+"/entries",Map.of("version","1","teamSeasonId",own)),409,"DUPLICATE_RESOURCE");
        ok(a.post("/competition-seasons/"+id+"/entries",Map.of("version","1","teamSeasonId",external)),200);
        assertThat(json(a.get("/competition-seasons/"+id+"/entries?size=1")).path("totalElements").asText()).isEqualTo("2");
        ok(a.delete("/competition-seasons/"+id+"/entries/"+own,Map.of("version","2")),200);
        assertThat(json(a.get("/competition-seasons/"+id)).path("season").path("version").asText()).isEqualTo("3");
        assertThat(json(a.get("/competition-seasons/"+id+"/entries")).path("totalElements").asText()).isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action='COMPETITION_ENTRY_REMOVED' AND entity_type='competition_entries' AND entity_key->>'competition_season_id'=? AND entity_key->>'team_season_id'=?",Long.class,id,own)).isEqualTo(1);
    }
    @Test void twoEditorsHaveOneWinnerAndAuditFailureRollsBackRulesVersionAndEntries() throws Exception {
        var f=fixture();var a=f.admin();String id=competitionSeason(f);var csrf=a.csrf();var gate=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        String body=mapper.writeValueAsString(Map.of("version","0","configuration",f.configuration()));
        try {
            var first=pool.submit(()->{gate.await();return a.send("PUT","/competition-seasons/"+id,body,csrf);});
            var second=pool.submit(()->{gate.await();return a.send("PUT","/competition-seasons/"+id,body,csrf);});gate.countDown();
            assertThat(List.of(first.get(30,TimeUnit.SECONDS).statusCode(),second.get(30,TimeUnit.SECONDS).statusCode())).containsExactlyInAnyOrder(200,409);
        } finally { pool.shutdownNow(); }
        String team=team(a,f.sport(),f.season(),"Club",true);
        jdbc.execute("ALTER TABLE audit_log ADD CONSTRAINT reject_p4_audit CHECK(action NOT LIKE 'COMPETITION_%') NOT VALID");
        try {
            error(a.put("/competition-seasons/"+id,Map.of("version","1","configuration",f.configuration())),409,"CONFLICT");
            error(a.post("/competition-seasons/"+id+"/entries",Map.of("version","1","teamSeasonId",team)),409,"CONFLICT");
            assertThat(json(a.get("/competition-seasons/"+id)).path("season").path("version").asText()).isEqualTo("1");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM competition_point_rules",Long.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM competition_entries",Long.class)).isZero();
        } finally { jdbc.execute("ALTER TABLE audit_log DROP CONSTRAINT reject_p4_audit"); }
    }
    @Test void locksArchiveAndReferencedFormatChangesPreserveValidConfigurationAndHistoricalEntries() throws Exception {
        var f=fixture();var a=f.admin();String id=competitionSeason(f);
        error(a.patch("/seasons/"+f.season(),Map.of("endsOn","2027-02-01")),409,"CONFIGURATION_LOCKED");
        assertThatThrownBy(()->jdbc.update("UPDATE seasons SET ends_on=DATE '2027-02-01' WHERE id=?",UUID.fromString(f.season()))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        String team=team(a,f.sport(),f.season(),"Club",true);
        ok(a.post("/competition-seasons/"+id+"/entries",Map.of("version","0","teamSeasonId",team)),200);
        error(a.put("/match-formats/"+f.format(),Map.of("version","0","name","Bad change","configuration",format(false,false))),409,"CONFIGURATION_LOCKED");
        ok(a.put("/match-formats/"+f.format(),Map.of("version","0","name","Renamed format","configuration",format(true,false))),200);
        jdbc.update("UPDATE competition_seasons SET locked_at=CURRENT_TIMESTAMP WHERE id=?",UUID.fromString(id));
        error(a.put("/competition-seasons/"+id,Map.of("version","1","configuration",f.configuration())),409,"CONFIGURATION_LOCKED");
        error(a.delete("/competition-seasons/"+id+"/entries/"+team,Map.of("version","1")),409,"CONFIGURATION_LOCKED");
        assertThatThrownBy(()->jdbc.update("UPDATE competition_seasons SET locked_at=NULL WHERE id=?",UUID.fromString(id))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM competition_point_rules WHERE competition_season_id=?",UUID.fromString(id))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE competition_forfeit_rules SET home_points=9 WHERE competition_season_id=?",UUID.fromString(id))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM competition_ranking_criteria WHERE competition_season_id=?",UUID.fromString(id))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM competition_entries WHERE competition_season_id=?",UUID.fromString(id))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        ok(a.post("/seasons/"+f.season()+"/archive",Map.of()),200);ok(a.get("/competition-seasons/"+id),200);
        assertThat(json(a.get("/competition-seasons/"+id+"/entries")).path("totalElements").asText()).isEqualTo("1");
    }
    @Test void adminOnlyIncludesCountsChildSubresourcesAndMutationsAndRequiresCsrf() throws Exception {
        var f=fixture();String id=competitionSeason(f);var user=account(true);var pending=account(false);var anonymous=new Browser();
        for(String path:List.of("/competitions","/competitions/"+f.competition(),"/competition-seasons","/competition-seasons/"+id,"/competition-seasons/"+id+"/entries","/competition-seasons/"+id+"/point-rules","/competition-seasons/"+id+"/ranking-criteria","/competition-seasons/"+id+"/forfeit-rules")) {
            error(user.get(path),403,"FORBIDDEN");error(pending.get(path),403,"PROFILE_INCOMPLETE");error(anonymous.get(path),401,"UNAUTHORIZED");
        }
        error(user.post("/competition-seasons",request(f,f.configuration())),403,"FORBIDDEN");
        error(user.put("/competition-seasons/"+id,Map.of("version","0","configuration",f.configuration())),403,"FORBIDDEN");
        error(f.admin().send("POST","/competition-seasons/"+id+"/entries",mapper.writeValueAsString(Map.of("version","0","teamSeasonId",UUID.randomUUID())),null),403,"FORBIDDEN");
        var forged=new HashMap<String,Object>();forged.put("version","0");forged.put("configuration",f.configuration());forged.put("lockedAt","2026-01-01T00:00:00Z");
        error(f.admin().put("/competition-seasons/"+id,forged),400,"BAD_REQUEST");
    }
    @Test void archivedSeasonAndInactiveSportRejectNewConfigurationAndArchivedUnlockedRulesAreReadOnly() throws Exception {
        var f=fixture();var a=f.admin();String id=competitionSeason(f);
        ok(a.patch("/sports/"+f.sport(),Map.of("active",false)),200);
        error(a.post("/competitions",Map.of("sportId",f.sport(),"name","Inactive")),409,"STATE_CONFLICT");
        error(a.post("/competition-seasons",request(f,f.configuration())),409,"STATE_CONFLICT");
        ok(a.patch("/sports/"+f.sport(),Map.of("active",true)),200);
        ok(a.post("/seasons/"+f.season()+"/archive",Map.of()),200);
        error(a.put("/competition-seasons/"+id,Map.of("version","0","configuration",f.configuration())),409,"CONFIGURATION_LOCKED");
        error(a.post("/competition-seasons",request(f,f.configuration())),409,"CONFIGURATION_LOCKED");
        ok(a.get("/competition-seasons/"+id),200);
    }
    private String team(Browser a,String sport,String season,String name,boolean club) throws Exception {
        String team=create(a,"/teams",Map.of("sportId",sport,"name",name,"isClubTeam",club));return create(a,"/team-seasons",Map.of("teamId",team,"seasonId",season,"displayName",name));
    }
    private Browser account(boolean complete) throws Exception {
        var a=new Browser();var credentials=Map.of("username","p4-"+UUID.randomUUID(),"password","test-password");ok(a.post("/auth/register",credentials),201);ok(a.post("/auth/login",credentials),200);
        if(complete) ok(a.post("/me/person",Map.of("firstName","Test","lastName","Account")),201);return a;
    }
    private String create(Browser a,String path,Object body) throws Exception { var r=a.post(path,body);ok(r,201);return json(r).path("id").asText(); }
    private JsonNode json(HttpResponse<String> r) throws Exception { return mapper.readTree(r.body()); }
    private void ok(HttpResponse<String> r,int status) { assertThat(r.statusCode()).as(r.body()).isEqualTo(status); }
    private void error(HttpResponse<String> r,int status,String code) throws Exception { ok(r,status);assertThat(json(r).path("code").asText()).isEqualTo(code); }
    private class Browser {
        final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        HttpResponse<String> get(String path) throws Exception { return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api"+path)).GET().build(),HttpResponse.BodyHandlers.ofString()); }
        JsonNode csrf() throws Exception { var r=get("/auth/csrf");ok(r,200);return json(r); }
        HttpResponse<String> post(String path,Object body) throws Exception { return send("POST",path,mapper.writeValueAsString(body),csrf()); }
        HttpResponse<String> put(String path,Object body) throws Exception { return send("PUT",path,mapper.writeValueAsString(body),csrf()); }
        HttpResponse<String> patch(String path,Object body) throws Exception { return send("PATCH",path,mapper.writeValueAsString(body),csrf()); }
        HttpResponse<String> delete(String path,Object body) throws Exception { return send("DELETE",path,mapper.writeValueAsString(body),csrf()); }
        HttpResponse<String> send(String method,String path,String body,JsonNode csrf) throws Exception {
            var r=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api"+path)).header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(body));
            if(csrf!=null)r.header(csrf.path("headerName").asText(),csrf.path("token").asText());return client.send(r.build(),HttpResponse.BodyHandlers.ofString());
        }
    }
}
