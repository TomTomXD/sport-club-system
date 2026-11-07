package cz.jpmad.springprojecttemplate.repository;

import cz.jpmad.springprojecttemplate.api.competition.dto.MatchFormatDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MatchFormatRepository {
    private final NamedParameterJdbcTemplate jdbc;
    // Both items and counts must retain exactly the same joins and filters.
    private static final String FROM = " FROM match_formats f JOIN sports s ON s.id=f.sport_id";
    private static final String SELECT = """
            SELECT f.id,f.sport_id,s.name sport_name,f.version,f.name,f.score_mode,f.allow_draw,
                   f.periods_required,f.period_label,f.regulation_period_count,f.wins_required,
                   f.allow_overtime,f.allow_shootout,f.shootout_winner_bonus,
                   f.friendly_forfeit_loser_score,f.friendly_forfeit_winner_score,f.locked_at
            """ + FROM;
    private static final RowMapper<Response> ROW = (r, n) -> {
        var lockedAt = r.getTimestamp("locked_at");
        return new Response(
            r.getObject("id", UUID.class), r.getObject("sport_id", UUID.class), r.getString("sport_name"),
            r.getString("version"), r.getString("name"), new Configuration(
                    ScoreMode.valueOf(r.getString("score_mode")), r.getBoolean("allow_draw"),
                    r.getBoolean("periods_required"), r.getString("period_label"),
                    r.getObject("regulation_period_count", Integer.class), r.getObject("wins_required", Integer.class),
                    r.getBoolean("allow_overtime"), r.getBoolean("allow_shootout"), r.getInt("shootout_winner_bonus"),
                    r.getInt("friendly_forfeit_loser_score"), r.getInt("friendly_forfeit_winner_score")),
            lockedAt == null ? null : lockedAt.toInstant());
    };

    public Page list(UUID sport, int page, int size) {
        String where = sport == null ? "" : " WHERE f.sport_id=:sport";
        var params = new MapSqlParameterSource("sport", sport);
        long total = Objects.requireNonNull(jdbc.queryForObject("SELECT count(*)" + FROM + where, params, Long.class));
        params.addValue("limit", size).addValue("offset", (long) page * size);
        return new Page(jdbc.query(SELECT + where + " ORDER BY f.name,f.id LIMIT :limit OFFSET :offset", params, ROW),
                page, size, Long.toString(total), (int) ((total + size - 1) / size));
    }
    public Response get(UUID id) {
        return jdbc.query(SELECT + " WHERE f.id=:id", new MapSqlParameterSource("id", id), ROW)
                .stream().findFirst().orElseThrow(NoSuchElementException::new);
    }
    public void insert(UUID id, UUID sport, String name, Configuration c) {
        jdbc.update("""
                INSERT INTO match_formats(id,sport_id,name,score_mode,allow_draw,periods_required,period_label,
                    regulation_period_count,wins_required,allow_overtime,allow_shootout,shootout_winner_bonus,
                    friendly_forfeit_loser_score,friendly_forfeit_winner_score)
                VALUES(:id,:sport,:name,:mode,:draw,:periods,:label,:count,:wins,:overtime,:shootout,:bonus,:loser,:winner)
                """, params(id, name, c).addValue("sport", sport));
    }
    public int update(UUID id, long version, String name, Configuration c) {
        return jdbc.update("""
                UPDATE match_formats SET name=:name,version=version+1,score_mode=:mode,allow_draw=:draw,
                    periods_required=:periods,period_label=:label,regulation_period_count=:count,wins_required=:wins,
                    allow_overtime=:overtime,allow_shootout=:shootout,shootout_winner_bonus=:bonus,
                    friendly_forfeit_loser_score=:loser,friendly_forfeit_winner_score=:winner
                WHERE id=:id AND version=:version
                """, params(id, name, c).addValue("version", version));
    }
    private MapSqlParameterSource params(UUID id, String name, Configuration c) {
        return new MapSqlParameterSource("id", id).addValue("name", name).addValue("mode", c.scoreMode().name())
                .addValue("draw", c.allowDraw()).addValue("periods", c.periodsRequired()).addValue("label", c.periodLabel())
                .addValue("count", c.regulationPeriodCount()).addValue("wins", c.winsRequired())
                .addValue("overtime", c.allowOvertime()).addValue("shootout", c.allowShootout())
                .addValue("bonus", c.shootoutWinnerBonus()).addValue("loser", c.friendlyForfeitLoserScore())
                .addValue("winner", c.friendlyForfeitWinnerScore());
    }
}
