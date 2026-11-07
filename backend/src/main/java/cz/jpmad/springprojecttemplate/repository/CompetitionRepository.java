package cz.jpmad.springprojecttemplate.repository;

import cz.jpmad.springprojecttemplate.api.competition.dto.CompetitionDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class CompetitionRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String COMP = "SELECT c.id,c.sport_id,s.name sport_name,c.name FROM competitions c JOIN sports s ON s.id=c.sport_id";
    private static final String SEASONS = """
            SELECT cs.*,c.name competition_name,c.sport_id,s.name sport_name,se.name season_name,se.archived,
                   f.name match_format_name
            FROM competition_seasons cs JOIN competitions c ON c.id=cs.competition_id
            JOIN sports s ON s.id=c.sport_id JOIN seasons se ON se.id=cs.season_id
            JOIN match_formats f ON f.id=cs.match_format_id
            """;
    private static final String ENTRIES = """
            SELECT e.team_season_id,ts.display_name,t.is_club_team FROM competition_entries e
            JOIN team_seasons ts ON ts.id=e.team_season_id JOIN teams t ON t.id=ts.team_id
            WHERE e.competition_season_id=:id
            """;
    private static final RowMapper<CompetitionResponse> COMP_ROW = (r,n) -> new CompetitionResponse(id(r,"id"),id(r,"sport_id"),r.getString("sport_name"),r.getString("name"));
    private static final RowMapper<SeasonSummary> SEASON_ROW = (r,n) -> {
        var locked = r.getTimestamp("locked_at");
        return new SeasonSummary(id(r,"id"),id(r,"competition_id"),r.getString("competition_name"),id(r,"season_id"),r.getString("season_name"),
                id(r,"sport_id"),r.getString("sport_name"),id(r,"match_format_id"),r.getString("match_format_name"),
                r.getString("version"),r.getString("results_revision"),locked == null ? null : locked.toInstant(),r.getBoolean("archived"));
    };
    private static UUID id(ResultSet r,String column) throws SQLException { return r.getObject(column,UUID.class); }
    private MapSqlParameterSource params(UUID id) { return new MapSqlParameterSource("id",id); }
    private <T> T one(String sql, MapSqlParameterSource p, RowMapper<T> row) {
        return jdbc.query(sql,p,row).stream().findFirst().orElseThrow(NoSuchElementException::new);
    }
    private <T> Page<T> page(String sql,MapSqlParameterSource p,String order,int page,int size,RowMapper<T> row) {
        long count = Objects.requireNonNull(jdbc.queryForObject("SELECT count(*) FROM (" + sql + ") scoped",p,Long.class));
        p.addValue("limit",size).addValue("offset",(long) page*size);
        return new Page<>(jdbc.query(sql+" ORDER BY "+order+" LIMIT :limit OFFSET :offset",p,row),page,size,Long.toString(count),(int)((count+size-1)/size));
    }
    public Page<CompetitionResponse> competitions(UUID sport,int page,int size) {
        return page(COMP+(sport==null?"":" WHERE c.sport_id=:sport"),new MapSqlParameterSource("sport",sport),"c.name,c.id",page,size,COMP_ROW);
    }
    public CompetitionResponse competition(UUID id) { return one(COMP+" WHERE c.id=:id",params(id),COMP_ROW); }
    public void insertCompetition(UUID id,UUID sport,String name) {
        jdbc.update("INSERT INTO competitions(id,sport_id,name) VALUES(:id,:sport,:name)",params(id).addValue("sport",sport).addValue("name",name));
    }
    public void renameCompetition(UUID id,String name) { jdbc.update("UPDATE competitions SET name=:name WHERE id=:id",params(id).addValue("name",name)); }
    public Page<SeasonSummary> seasons(UUID sport,UUID competition,UUID season,int page,int size) {
        String where=" WHERE TRUE"+(sport==null?"":" AND c.sport_id=:sport")+(competition==null?"":" AND cs.competition_id=:competition")+(season==null?"":" AND cs.season_id=:season");
        return page(SEASONS+where,new MapSqlParameterSource("sport",sport).addValue("competition",competition).addValue("season",season),"c.name,se.starts_on DESC,cs.id",page,size,SEASON_ROW);
    }
    public SeasonSummary season(UUID id) { return one(SEASONS+" WHERE cs.id=:id",params(id),SEASON_ROW); }
    public boolean seasonExists(UUID competition,UUID season) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM competition_seasons WHERE competition_id=:c AND season_id=:s)",
                new MapSqlParameterSource("c",competition).addValue("s",season),Boolean.class));
    }
    public void insertSeason(UUID id,UUID competition,UUID season,UUID format,Configuration c) {
        jdbc.update("""
                INSERT INTO competition_seasons(id,competition_id,season_id,match_format_id,head_to_head_missing_policy,
                    head_to_head_meetings_per_pair,restart_head_to_head_on_subgroup)
                VALUES(:id,:competition,:season,:format,:policy,:meetings,:restart)
                """, configurationParams(id,c).addValue("competition",competition).addValue("season",season).addValue("format",format));
    }
    public int updateSeason(UUID id,long version,Configuration c) {
        return jdbc.update("""
                UPDATE competition_seasons SET version=version+1,head_to_head_missing_policy=:policy,
                    head_to_head_meetings_per_pair=:meetings,restart_head_to_head_on_subgroup=:restart
                WHERE id=:id AND version=:version
                """,configurationParams(id,c).addValue("version",version));
    }
    public int incrementVersion(UUID id,long version) {
        return jdbc.update("UPDATE competition_seasons SET version=version+1 WHERE id=:id AND version=:version",params(id).addValue("version",version));
    }
    private MapSqlParameterSource configurationParams(UUID id,Configuration c) {
        return params(id).addValue("policy",c.headToHeadMissingPolicy().name()).addValue("meetings",c.headToHeadMeetingsPerPair()).addValue("restart",c.restartHeadToHeadOnSubgroup());
    }
    public Configuration configuration(UUID id) {
        var p=params(id);
        var points=jdbc.query("SELECT * FROM competition_point_rules WHERE competition_season_id=:id ORDER BY outcome,ending,own_score_min NULLS FIRST,opponent_score_min NULLS FIRST,id",p,
                (r,n)->new PointRule(Outcome.valueOf(r.getString("outcome")),Ending.valueOf(r.getString("ending")),r.getObject("own_score_min",Integer.class),r.getObject("own_score_max",Integer.class),
                        r.getObject("opponent_score_min",Integer.class),r.getObject("opponent_score_max",Integer.class),r.getInt("points")));
        var forfeits=jdbc.query("SELECT * FROM competition_forfeit_rules WHERE competition_season_id=:id ORDER BY decision",p,
                (r,n)->new ForfeitRule(Decision.valueOf(r.getString("decision")),r.getInt("home_score"),r.getInt("away_score"),r.getInt("home_points"),r.getInt("away_points")));
        var ranking=jdbc.query("SELECT * FROM competition_ranking_criteria WHERE competition_season_id=:id ORDER BY priority",p,
                (r,n)->new RankingCriterion(r.getInt("priority"),Metric.valueOf(r.getString("metric")),Direction.valueOf(r.getString("direction"))));
        return one("SELECT * FROM competition_seasons WHERE id=:id",p,(r,n)->new Configuration(MissingPolicy.valueOf(r.getString("head_to_head_missing_policy")),r.getInt("head_to_head_meetings_per_pair"),
                r.getBoolean("restart_head_to_head_on_subgroup"),points,forfeits,ranking));
    }
    public List<UUID> seasonsUsingFormat(UUID format) {
        return jdbc.query("SELECT id FROM competition_seasons WHERE match_format_id=:format",new MapSqlParameterSource("format",format),(r,n)->id(r,"id"));
    }
    public void replaceRules(UUID id,Configuration c) {
        var p=params(id);
        jdbc.update("DELETE FROM competition_point_rules WHERE competition_season_id=:id",p);
        jdbc.update("DELETE FROM competition_forfeit_rules WHERE competition_season_id=:id",p);
        jdbc.update("DELETE FROM competition_ranking_criteria WHERE competition_season_id=:id",p);
        jdbc.batchUpdate("""
                INSERT INTO competition_point_rules(id,competition_season_id,outcome,ending,own_score_min,own_score_max,opponent_score_min,opponent_score_max,points)
                VALUES(:key,:id,:outcome,:ending,:ownMin,:ownMax,:opponentMin,:opponentMax,:points)
                """,c.pointRules().stream().map(r->params(id).addValue("key",UUID.randomUUID()).addValue("outcome",r.outcome().name()).addValue("ending",r.ending().name())
                .addValue("ownMin",r.ownScoreMin()).addValue("ownMax",r.ownScoreMax()).addValue("opponentMin",r.opponentScoreMin()).addValue("opponentMax",r.opponentScoreMax()).addValue("points",r.points())).toArray(SqlParameterSource[]::new));
        jdbc.batchUpdate("INSERT INTO competition_forfeit_rules VALUES(:id,:decision,:home,:away,:homePoints,:awayPoints)",c.forfeitRules().stream()
                .map(r->params(id).addValue("decision",r.decision().name()).addValue("home",r.homeScore()).addValue("away",r.awayScore()).addValue("homePoints",r.homePoints()).addValue("awayPoints",r.awayPoints())).toArray(SqlParameterSource[]::new));
        jdbc.batchUpdate("INSERT INTO competition_ranking_criteria VALUES(:id,:priority,:metric,:direction)",c.rankingCriteria().stream()
                .map(r->params(id).addValue("priority",r.priority()).addValue("metric",r.metric().name()).addValue("direction",r.direction().name())).toArray(SqlParameterSource[]::new));
    }
    public Page<EntryResponse> entries(UUID id,int page,int size) {
        return page(ENTRIES,params(id),"ts.display_name,e.team_season_id",page,size,(r,n)->new EntryResponse(id(r,"team_season_id"),r.getString("display_name"),r.getBoolean("is_club_team")));
    }
    public boolean entryExists(UUID id,UUID team) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM competition_entries WHERE competition_season_id=:id AND team_season_id=:team)",params(id).addValue("team",team),Boolean.class));
    }
    public void addEntry(UUID id,UUID team) { jdbc.update("INSERT INTO competition_entries VALUES(:id,:team)",params(id).addValue("team",team)); }
    public void removeEntry(UUID id,UUID team) { jdbc.update("DELETE FROM competition_entries WHERE competition_season_id=:id AND team_season_id=:team",params(id).addValue("team",team)); }
}
