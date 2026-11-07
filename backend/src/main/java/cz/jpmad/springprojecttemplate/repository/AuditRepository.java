package cz.jpmad.springprojecttemplate.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

/** Audit obsahuje pouze identifikaci cíle a druh operace, nikdy obsah requestu. */
@Repository
@RequiredArgsConstructor
public class AuditRepository {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public void record(UUID actorId, String action, String entityType, Object targetId) { record(actorId,action,entityType,targetId,null); }
    public void record(UUID actorId, String action, String entityType, Object targetId,String reason) {
        String key = targetId instanceof Number ? "{\"id\":" + targetId + "}" : "{\"id\":\"" + targetId + "\"}";
        jdbc.update("INSERT INTO audit_log (id, actor_user_id, occurred_at, action, entity_type, entity_key, reason) VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?)",
                UUID.randomUUID(), actorId, Timestamp.from(clock.instant()), action, entityType, key,reason);
    }
    public void recordGuardian(UUID actorId,String action,UUID guardian,UUID player,String reason) {
        jdbc.update("INSERT INTO audit_log(id,actor_user_id,occurred_at,action,entity_type,entity_key,reason) VALUES(?,?,?,?,?,jsonb_build_object('guardian_person_id',CAST(? AS text),'player_id',CAST(? AS text)),?)",
                UUID.randomUUID(),actorId,Timestamp.from(clock.instant()),action,"guardian_links",guardian.toString(),player.toString(),reason);
    }
    public void recordCompetitionEntry(UUID actorId,String action,UUID competitionSeason,UUID teamSeason) {
        jdbc.update("INSERT INTO audit_log(id,actor_user_id,occurred_at,action,entity_type,entity_key) VALUES(?,?,?,?,?,jsonb_build_object('competition_season_id',CAST(? AS text),'team_season_id',CAST(? AS text)))",
                UUID.randomUUID(),actorId,Timestamp.from(clock.instant()),action,"competition_entries",competitionSeason.toString(),teamSeason.toString());
    }
}
