package cz.jpmad.springprojecttemplate.repository;

import cz.jpmad.springprojecttemplate.api.person.dto.PersonResponse;
import cz.jpmad.springprojecttemplate.api.team.dto.TeamDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/** Scope i počty jsou součástí SQL dotazu, nikoli filtrování již načtených osob. */
@Repository
@RequiredArgsConstructor
public class TeamDomainRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String TS = "SELECT ts.id,ts.team_id,ts.season_id,t.sport_id,sp.name sport_name,t.name team_name,ts.display_name,t.is_club_team,s.name season_name,s.starts_on,s.ends_on,s.archived FROM team_seasons ts JOIN teams t ON t.id=ts.team_id JOIN sports sp ON sp.id=t.sport_id JOIN seasons s ON s.id=ts.season_id";
    private static final String COACH_SCOPE = "EXISTS (SELECT 1 FROM coach_team_memberships cm WHERE cm.team_season_id=ts.id AND cm.coach_user_id=:actor AND EXISTS (SELECT 1 FROM users cu WHERE cu.id=:actor AND cu.enabled AND cu.account_type='STANDARD') AND cm.valid_from<=:today AND COALESCE(cm.valid_to,s.ends_on)>:today)";
    private static final String PLAYER_SCOPE = "EXISTS (SELECT 1 FROM player_team_memberships pm JOIN player_profiles pp ON pp.id=pm.player_id WHERE pm.team_season_id=ts.id AND pp.person_id=:person AND pm.valid_from<=:today AND COALESCE(pm.valid_to,s.ends_on)>:today)";
    private static final String PARENT_SCOPE = "EXISTS (SELECT 1 FROM guardian_links gl JOIN player_team_memberships gm ON gm.player_id=gl.player_id JOIN users gu ON gu.person_id=gl.guardian_person_id WHERE gm.team_season_id=ts.id AND gu.id=:actor AND gu.person_id=:person AND gu.enabled AND gu.account_type='STANDARD' AND gl.revoked_at IS NULL AND gm.valid_from<=:today AND COALESCE(gm.valid_to,s.ends_on)>:today)";
    private static final RowMapper<SeasonResponse> SEASON = (r,n)-> new SeasonResponse(id(r,"id"),r.getString("name"),date(r,"starts_on"),date(r,"ends_on"),r.getBoolean("archived"));
    private static final RowMapper<TeamResponse> TEAM = (r,n)->new TeamResponse(id(r,"id"),id(r,"sport_id"),r.getString("sport_name"),r.getString("name"),r.getBoolean("is_club_team"));
    private static final RowMapper<TeamSeasonResponse> TEAM_SEASON = (r,n)->new TeamSeasonResponse(id(r,"id"),id(r,"team_id"),id(r,"season_id"),id(r,"sport_id"),r.getString("sport_name"),r.getString("team_name"),r.getString("display_name"),r.getBoolean("is_club_team"),r.getString("season_name"),date(r,"starts_on"),date(r,"ends_on"),r.getBoolean("archived"));
    private static final RowMapper<PersonResponse> PERSON = (r,n)->new PersonResponse(id(r,"id"),r.getString("first_name"),r.getString("last_name"),date(r,"birth_date"),r.getString("email"),r.getString("phone"));
    private static final String PLAYER = "SELECT pp.id,p.id person_id,p.first_name,p.last_name,p.birth_date FROM player_profiles pp JOIN persons p ON p.id=pp.person_id";
    private static final RowMapper<PlayerResponse> PLAYER_ROW = (r,n)->new PlayerResponse(id(r,"id"),id(r,"person_id"),r.getString("first_name"),r.getString("last_name"),date(r,"birth_date"));
    private static final String PM = "SELECT m.*,p.first_name,p.last_name FROM player_team_memberships m JOIN player_profiles pp ON pp.id=m.player_id JOIN persons p ON p.id=pp.person_id";
    private static final RowMapper<PlayerMembershipResponse> PM_ROW = (r,n)->new PlayerMembershipResponse(id(r,"id"),id(r,"team_season_id"),id(r,"player_id"),r.getString("first_name"),r.getString("last_name"),date(r,"valid_from"),date(r,"valid_to"));
    private static final String CM = "SELECT m.*,u.username,p.first_name,p.last_name FROM coach_team_memberships m JOIN users u ON u.id=m.coach_user_id JOIN persons p ON p.id=u.person_id";
    private static final RowMapper<CoachMembershipResponse> CM_ROW = (r,n)->new CoachMembershipResponse(id(r,"id"),id(r,"team_season_id"),id(r,"coach_user_id"),r.getString("username"),r.getString("first_name"),r.getString("last_name"),date(r,"valid_from"),date(r,"valid_to"));
    private static UUID id(ResultSet r,String field) throws SQLException { return r.getObject(field,UUID.class); }
    private static LocalDate date(ResultSet r,String field) throws SQLException { return r.getObject(field,LocalDate.class); }
    public MapSqlParameterSource params() { return new MapSqlParameterSource(); }
    private <T> T one(String sql,MapSqlParameterSource p,RowMapper<T> mapper) {
        return jdbc.query(sql,p,mapper).stream().findFirst().orElseThrow(NoSuchElementException::new);
    }
    private <T> Page<T> page(String sql,MapSqlParameterSource p,String order,int page,int size,RowMapper<T> mapper) {
        long count = Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM ("+sql+") scoped",p,Long.class));
        p.addValue("limit",size).addValue("offset",(long)page*size);
        return new Page<>(jdbc.query(sql+" ORDER BY "+order+" LIMIT :limit OFFSET :offset",p,mapper),page,size,Long.toString(count),(int)((count+size-1)/size));
    }
    public Page<SeasonResponse> seasons(int page,int size) { return page("SELECT * FROM seasons",params(),"starts_on DESC,id",page,size,SEASON); }
    public SeasonResponse season(UUID id) { return one("SELECT * FROM seasons WHERE id=:id",params().addValue("id",id),SEASON); }
    // Later event/fee deliveries must extend this check with their season references.
    public boolean seasonUsed(UUID id) { return exists("SELECT (SELECT count(*) FROM team_seasons WHERE season_id=:id)+(SELECT count(*) FROM competition_seasons WHERE season_id=:id)",params().addValue("id",id)); }
    public boolean seasonNameUsed(String name,UUID except) { return exists("SELECT count(*) FROM seasons WHERE name=:name AND id<>:id",params().addValue("name",name).addValue("id",except)); }
    public void insertSeason(UUID id,String name,LocalDate from,LocalDate to) { jdbc.update("INSERT INTO seasons(id,name,starts_on,ends_on) VALUES(:id,:name,:f,:t)",params().addValue("id",id).addValue("name",name).addValue("f",from).addValue("t",to)); }
    public void updateSeason(UUID id,String name,LocalDate from,LocalDate to) { jdbc.update("UPDATE seasons SET name=:name,starts_on=:f,ends_on=:t WHERE id=:id",params().addValue("id",id).addValue("name",name).addValue("f",from).addValue("t",to)); }
    public void archiveSeason(UUID id) { jdbc.update("UPDATE seasons SET archived=TRUE WHERE id=:id",params().addValue("id",id)); }
    public Page<TeamResponse> teams(UUID sport,int page,int size) { return page("SELECT t.*,sp.name sport_name FROM teams t JOIN sports sp ON sp.id=t.sport_id"+(sport==null?"":" WHERE t.sport_id=:sport"),params().addValue("sport",sport),"t.name,t.id",page,size,TEAM); }
    public TeamResponse team(UUID id) { return one("SELECT t.*,sp.name sport_name FROM teams t JOIN sports sp ON sp.id=t.sport_id WHERE t.id=:id",params().addValue("id",id),TEAM); }
    public boolean teamUsed(UUID id) { return exists("SELECT count(*) FROM team_seasons WHERE team_id=:id",params().addValue("id",id)); }
    public boolean sportActive(UUID id) { return jdbc.query("SELECT active FROM sports WHERE id=:id",params().addValue("id",id),(r,n)->r.getBoolean("active")).stream().findFirst().orElseThrow(NoSuchElementException::new); }
    public void insertTeam(UUID id,UUID sport,String name,boolean club) { jdbc.update("INSERT INTO teams(id,sport_id,name,is_club_team) VALUES(:id,:sport,:name,:club)",params().addValue("id",id).addValue("sport",sport).addValue("name",name).addValue("club",club)); }
    public void updateTeam(UUID id,UUID sport,String name,boolean club) { jdbc.update("UPDATE teams SET sport_id=:sport,name=:name,is_club_team=:club WHERE id=:id",params().addValue("id",id).addValue("sport",sport).addValue("name",name).addValue("club",club)); }
    public TeamSeasonResponse teamSeason(UUID id) { return one(TS+" WHERE ts.id=:id",params().addValue("id",id),TEAM_SEASON); }
    public boolean pairUsed(UUID team,UUID season) { return exists("SELECT count(*) FROM team_seasons WHERE team_id=:team AND season_id=:season",params().addValue("team",team).addValue("season",season)); }
    public void insertTeamSeason(UUID id,UUID team,UUID season,String name) { jdbc.update("INSERT INTO team_seasons(id,team_id,season_id,display_name) VALUES(:id,:team,:season,:name)",params().addValue("id",id).addValue("team",team).addValue("season",season).addValue("name",name)); }
    public void renameTeamSeason(UUID id,String name) { jdbc.update("UPDATE team_seasons SET display_name=:name WHERE id=:id",params().addValue("id",id).addValue("name",name)); }
    private MapSqlParameterSource scope(UUID actor,UUID person,LocalDate today) { return params().addValue("actor",actor).addValue("person",person).addValue("today",today); }
    public boolean hasTeams(UUID actor,UUID person,LocalDate today) { return exists("SELECT count(*) FROM ("+TS+" WHERE "+COACH_SCOPE+" OR "+PLAYER_SCOPE+" OR "+PARENT_SCOPE+") q",scope(actor,person,today)); }
    public boolean canReadTeam(UUID ts,UUID actor,UUID person,LocalDate today) { return exists("SELECT count(*) FROM ("+TS+" WHERE ts.id=:id AND ("+COACH_SCOPE+" OR "+PLAYER_SCOPE+" OR "+PARENT_SCOPE+")) q",scope(actor,person,today).addValue("id",ts)); }
    public Page<TeamSeasonResponse> teamSeasons(UUID actor,UUID person,LocalDate today,boolean admin,UUID season,UUID sport,int page,int size) {
        String where=" WHERE TRUE"+(admin?"":" AND ("+COACH_SCOPE+" OR "+PLAYER_SCOPE+" OR "+PARENT_SCOPE+")")+(season==null?"":" AND ts.season_id=:season")+(sport==null?"":" AND t.sport_id=:sport");
        return page(TS+where,scope(actor,person,today).addValue("season",season).addValue("sport",sport),"s.starts_on DESC,ts.display_name,ts.id",page,size,TEAM_SEASON);
    }
    public List<UUID> currentTeamIds(UUID actor,UUID person,LocalDate today) { return jdbc.query("SELECT q.id FROM ("+TS+" WHERE "+COACH_SCOPE+" OR "+PLAYER_SCOPE+" OR "+PARENT_SCOPE+") q ORDER BY q.id",scope(actor,person,today),(r,n)->id(r,"id")); }
    public Page<RosterResponse> roster(UUID ts,LocalDate on,int page,int size) {
        String sql="SELECT m.player_id,p.first_name,p.last_name,m.id membership_id,m.valid_from,m.valid_to FROM player_team_memberships m JOIN player_profiles pp ON pp.id=m.player_id JOIN persons p ON p.id=pp.person_id JOIN team_seasons ts ON ts.id=m.team_season_id JOIN seasons s ON s.id=ts.season_id WHERE m.team_season_id=:ts AND m.valid_from<=:on AND COALESCE(m.valid_to,s.ends_on)>:on";
        return page(sql,params().addValue("ts",ts).addValue("on",on),"p.last_name,p.first_name,m.id",page,size,(r,n)->new RosterResponse(id(r,"player_id"),r.getString("first_name"),r.getString("last_name"),id(r,"membership_id"),date(r,"valid_from"),date(r,"valid_to")));
    }
    public Page<PersonResponse> persons(int page,int size) { return page("SELECT * FROM persons",params(),"last_name,first_name,id",page,size,PERSON); }
    public PersonResponse person(UUID id) { return one("SELECT * FROM persons WHERE id=:id",params().addValue("id",id),PERSON); }
    public void insertPerson(UUID id,String first,String last,LocalDate dob,String email,String phone) { jdbc.update("INSERT INTO persons(id,first_name,last_name,birth_date,email,phone) VALUES(:id,:f,:l,:dob,:email,:phone)",params().addValue("id",id).addValue("f",first).addValue("l",last).addValue("dob",dob).addValue("email",email).addValue("phone",phone)); }
    public void updatePerson(UUID id,String first,String last,LocalDate dob,String email,String phone) { jdbc.update("UPDATE persons SET first_name=:f,last_name=:l,birth_date=:dob,email=:email,phone=:phone WHERE id=:id",params().addValue("id",id).addValue("f",first).addValue("l",last).addValue("dob",dob).addValue("email",email).addValue("phone",phone)); }
    public Optional<UUID> ownPlayer(UUID person) { return jdbc.query("SELECT id FROM player_profiles WHERE person_id=:person",params().addValue("person",person),(r,n)->id(r,"id")).stream().findFirst(); }
    public void insertPlayer(UUID id,UUID person) { jdbc.update("INSERT INTO player_profiles(id,person_id) VALUES(:id,:person)",params().addValue("id",id).addValue("person",person)); }
    public Page<PlayerResponse> players(int page,int size) { return page(PLAYER,params(),"p.last_name,p.first_name,pp.id",page,size,PLAYER_ROW); }
    public PlayerResponse player(UUID id) { return one(PLAYER+" WHERE pp.id=:id",params().addValue("id",id),PLAYER_ROW); }
    public boolean coachCanReadPlayer(UUID player,UUID actor,LocalDate today) {
        return exists("SELECT count(*) FROM player_team_memberships pm JOIN team_seasons ts ON ts.id=pm.team_season_id JOIN seasons s ON s.id=ts.season_id WHERE pm.player_id=:player AND pm.valid_from<=:today AND COALESCE(pm.valid_to,s.ends_on)>:today AND "+COACH_SCOPE,scope(actor,null,today).addValue("player",player));
    }
    public boolean parentCanReadPlayer(UUID player,UUID person) {
        return exists("SELECT count(*) FROM guardian_links gl JOIN users u ON u.person_id=gl.guardian_person_id WHERE gl.player_id=:player AND gl.guardian_person_id=:person AND gl.revoked_at IS NULL AND u.enabled AND u.account_type='STANDARD'",params().addValue("player",player).addValue("person",person));
    }
    public Page<PlayerHistoryResponse> playerHistory(UUID player,UUID actor,LocalDate today,boolean all,int page,int size) {
        String sql="SELECT pm.id membership_id,ts.id team_season_id,ts.display_name,s.name season_name,sp.name sport_name,pm.valid_from,pm.valid_to FROM player_team_memberships pm JOIN team_seasons ts ON ts.id=pm.team_season_id JOIN seasons s ON s.id=ts.season_id JOIN teams t ON t.id=ts.team_id JOIN sports sp ON sp.id=t.sport_id WHERE pm.player_id=:player"+(all?"":" AND "+COACH_SCOPE);
        return page(sql,scope(actor,null,today).addValue("player",player),"s.starts_on DESC,pm.valid_from,pm.id",page,size,(r,n)->new PlayerHistoryResponse(id(r,"membership_id"),id(r,"team_season_id"),r.getString("display_name"),r.getString("season_name"),r.getString("sport_name"),date(r,"valid_from"),date(r,"valid_to")));
    }
    public Page<PlayerMembershipResponse> playerMemberships(UUID ts,int page,int size) { return page(PM+" WHERE m.team_season_id=:ts",params().addValue("ts",ts),"m.valid_from,m.id",page,size,PM_ROW); }
    public PlayerMembershipResponse playerMembership(UUID id) { return one(PM+" WHERE m.id=:id",params().addValue("id",id),PM_ROW); }
    public Page<CoachMembershipResponse> coachMemberships(UUID ts,int page,int size) { return page(CM+" WHERE m.team_season_id=:ts",params().addValue("ts",ts),"m.valid_from,m.id",page,size,CM_ROW); }
    public CoachMembershipResponse coachMembership(UUID id) { return one(CM+" WHERE m.id=:id",params().addValue("id",id),CM_ROW); }
    public void insertPlayerMembership(UUID id,UUID ts,UUID player,LocalDate from,LocalDate to) { jdbc.update("INSERT INTO player_team_memberships(id,team_season_id,player_id,valid_from,valid_to) VALUES(:id,:ts,:member,:f,:t)",membershipParams(id,ts,player,from,to)); }
    public void insertCoachMembership(UUID id,UUID ts,UUID coach,LocalDate from,LocalDate to) { jdbc.update("INSERT INTO coach_team_memberships(id,team_season_id,coach_user_id,valid_from,valid_to) VALUES(:id,:ts,:member,:f,:t)",membershipParams(id,ts,coach,from,to)); }
    public void updatePlayerMembership(UUID id,LocalDate from,LocalDate to) { jdbc.update("UPDATE player_team_memberships SET valid_from=:f,valid_to=:t WHERE id=:id",params().addValue("id",id).addValue("f",from).addValue("t",to)); }
    public void updateCoachMembership(UUID id,LocalDate from,LocalDate to) { jdbc.update("UPDATE coach_team_memberships SET valid_from=:f,valid_to=:t WHERE id=:id",params().addValue("id",id).addValue("f",from).addValue("t",to)); }
    private MapSqlParameterSource membershipParams(UUID id,UUID ts,UUID member,LocalDate from,LocalDate to) { return params().addValue("id",id).addValue("ts",ts).addValue("member",member).addValue("f",from).addValue("t",to); }
    public boolean playerOverlap(UUID ts,UUID member,UUID except,LocalDate from,LocalDate to) { return exists("SELECT count(*) FROM player_team_memberships WHERE team_season_id=:ts AND player_id=:member AND id<>:id AND daterange(valid_from,valid_to,'[)') && daterange(CAST(:f AS date),CAST(:t AS date),'[)')",membershipParams(except,ts,member,from,to)); }
    public boolean coachOverlap(UUID ts,UUID member,UUID except,LocalDate from,LocalDate to) { return exists("SELECT count(*) FROM coach_team_memberships WHERE team_season_id=:ts AND coach_user_id=:member AND id<>:id AND daterange(valid_from,valid_to,'[)') && daterange(CAST(:f AS date),CAST(:t AS date),'[)')",membershipParams(except,ts,member,from,to)); }
    private static final String CANDIDATES="SELECT u.id,u.username,p.first_name,p.last_name FROM users u JOIN persons p ON p.id=u.person_id WHERE u.enabled AND u.account_type='STANDARD'";
    public boolean eligibleCoach(UUID id) { return exists("SELECT count(*) FROM ("+CANDIDATES+" AND u.id=:id) q",params().addValue("id",id)); }
    public Page<CoachCandidateResponse> coachCandidates(int page,int size) { return page(CANDIDATES,params(),"p.last_name,p.first_name,u.id",page,size,(r,n)->new CoachCandidateResponse(id(r,"id"),r.getString("username"),r.getString("first_name"),r.getString("last_name"))); }
    private boolean exists(String sql,MapSqlParameterSource p) { Long n=jdbc.queryForObject(sql,p,Long.class); return n!=null && n>0; }
}
