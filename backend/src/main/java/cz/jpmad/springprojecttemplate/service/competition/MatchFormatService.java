package cz.jpmad.springprojecttemplate.service.competition;

import cz.jpmad.springprojecttemplate.api.ApiException;
import cz.jpmad.springprojecttemplate.api.competition.dto.MatchFormatDtos.*;
import cz.jpmad.springprojecttemplate.repository.*;
import cz.jpmad.springprojecttemplate.service.user.CurrentAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class MatchFormatService {
    private final ClubRepository clubs;
    private final CurrentAccountService accounts;
    private final MatchFormatRepository formats;
    private final SportRepository sports;
    private final AuditRepository audit;
    private final CompetitionRepository competitions;
    private final CompetitionConfigurationValidator competitionValidator;

    public Page list(UUID actor, UUID sport, int page, int size) {
        clubs.lockForRead();
        accounts.requireAdmin(actor);
        if (page < 0) throw ApiException.invalidField("page", "Stránka nesmí být záporná.");
        if (size < 1 || size > 100) throw ApiException.invalidField("size", "Velikost stránky musí být 1–100.");
        return formats.list(sport, page, size);
    }
    public Response get(UUID actor, UUID id) {
        clubs.lockForRead();
        accounts.requireAdmin(actor);
        return formats.get(id);
    }
    public Response create(UUID actor, CreateRequest r) {
        clubs.lockForWrite();
        accounts.requireAdmin(actor);
        var sport = sports.findById(r.sportId()).orElseThrow(NoSuchElementException::new);
        if (!sport.isActive()) throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "Sport není aktivní.");
        Configuration c = normalize(r.configuration());
        validate(c);
        UUID id = UUID.randomUUID();
        formats.insert(id, r.sportId(), r.name().strip(), c);
        audit.record(actor, "MATCH_FORMAT_CREATED", "match_formats", id);
        return formats.get(id);
    }
    public Response update(UUID actor, UUID id, UpdateRequest r) {
        clubs.lockForWrite();
        accounts.requireAdmin(actor);
        Response old = formats.get(id);
        long version;
        try { version = Long.parseLong(r.version()); }
        catch (NumberFormatException ex) { throw ApiException.invalidField("version", "Neplatná verze záznamu."); }
        if (!old.version().equals(r.version())) throw stale();
        Configuration c = normalize(r.configuration());
        validate(c);
        if (old.lockedAt() != null && !old.configuration().equals(c)) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFIGURATION_LOCKED", "Použitý formát lze pouze přejmenovat.");
        }
        if (!old.configuration().equals(c)) {
            for (UUID season : competitions.seasonsUsingFormat(id)) {
                var usage = competitions.season(season);
                if (usage.archived() || usage.lockedAt() != null) {
                    throw new ApiException(HttpStatus.CONFLICT, "CONFIGURATION_LOCKED", "Formát používá uzamčený nebo archivovaný ročník.");
                }
                try { competitionValidator.validate(competitions.configuration(season), c); }
                catch (ApiException invalid) {
                    throw new ApiException(HttpStatus.CONFLICT, "CONFIGURATION_LOCKED", "Změna by zneplatnila pravidla připojeného ročníku. Vytvořte nový formát a ročník.");
                }
            }
        }
        if (version == Long.MAX_VALUE) throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "Verzi již nelze zvýšit.");
        if (formats.update(id, version, r.name().strip(), c) != 1) throw stale();
        audit.record(actor, "MATCH_FORMAT_UPDATED", "match_formats", id);
        return formats.get(id);
    }
    private ApiException stale() {
        return new ApiException(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", "Formát byl mezitím změněn. Načtěte aktuální verzi.");
    }
    private Configuration normalize(Configuration c) {
        return new Configuration(c.scoreMode(), c.allowDraw(), c.periodsRequired(), c.periodLabel().strip(),
                c.regulationPeriodCount(), c.winsRequired(), c.allowOvertime(), c.allowShootout(),
                c.shootoutWinnerBonus(), c.friendlyForfeitLoserScore(), c.friendlyForfeitWinnerScore());
    }
    private void validate(Configuration c) {
        if (c.friendlyForfeitWinnerScore() <= c.friendlyForfeitLoserScore()) {
            throw ApiException.invalidField("configuration.friendlyForfeitWinnerScore", "Skóre vítěze musí být vyšší než skóre poraženého.");
        }
        if (!c.allowShootout() && c.shootoutWinnerBonus() != 0) {
            throw ApiException.invalidField("configuration.shootoutWinnerBonus", "Bonus vyžaduje povolené nájezdy.");
        }
        if (c.scoreMode() == ScoreMode.SUM_SCORE && c.winsRequired() != null) {
            throw ApiException.invalidField("configuration.winsRequired", "Součet skóre nepoužívá počet vítězných částí.");
        }
        if (c.scoreMode() == ScoreMode.WON_PARTS && (!c.periodsRequired() || c.winsRequired() == null
                || c.allowDraw() || c.allowOvertime() || c.allowShootout() || c.regulationPeriodCount() != null)) {
            throw ApiException.invalidField("configuration.scoreMode", "Vyhrané části vyžadují povinné části a počet vítězství, bez remíz, prodloužení, nájezdů a pevného počtu částí.");
        }
    }
}
