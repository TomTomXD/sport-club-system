package cz.jpmad.springprojecttemplate.service.competition;

import cz.jpmad.springprojecttemplate.api.ApiException;
import cz.jpmad.springprojecttemplate.api.competition.dto.CompetitionDtos.*;
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
public class CompetitionService {
    private final ClubRepository clubs;
    private final CurrentAccountService accounts;
    private final CompetitionRepository data;
    private final MatchFormatRepository formats;
    private final TeamDomainRepository teams;
    private final CompetitionConfigurationValidator validator;
    private final AuditRepository audit;

    private void actor(UUID actor,boolean write) {
        if (write) clubs.lockForWrite(); else clubs.lockForRead();
        accounts.requireAdmin(actor);
    }
    private void page(int page,int size) {
        if (page<0) throw ApiException.invalidField("page","Stránka nesmí být záporná.");
        if (size<1 || size>100) throw ApiException.invalidField("size","Velikost stránky musí být 1–100.");
    }
    private void active(UUID sport) {
        if (!teams.sportActive(sport)) conflict("STATE_CONFLICT","Sport není aktivní.");
    }
    private void open(SeasonSummary s) {
        if (s.archived() || s.lockedAt()!=null) conflict("CONFIGURATION_LOCKED","Ročník je použitý nebo sezóna archivovaná.");
    }
    private long version(String expected,SeasonSummary s) {
        long value;
        try { value=Long.parseLong(expected); }
        catch (NumberFormatException ex) { throw ApiException.invalidField("version","Neplatná verze."); }
        if (!s.version().equals(expected)) conflict("OPTIMISTIC_LOCK_CONFLICT","Ročník byl mezitím změněn.");
        if (value==Long.MAX_VALUE) conflict("STATE_CONFLICT","Verzi již nelze zvýšit.");
        return value;
    }
    private void conflict(String code,String message) { throw new ApiException(HttpStatus.CONFLICT,code,message); }
    private SeasonResponse response(UUID id) { return new SeasonResponse(data.season(id),data.configuration(id)); }

    public Page<CompetitionResponse> competitions(UUID actor,UUID sport,int page,int size) {
        actor(actor,false); page(page,size); return data.competitions(sport,page,size);
    }
    public CompetitionResponse competition(UUID actor,UUID id) { actor(actor,false); return data.competition(id); }
    public CompetitionResponse createCompetition(UUID actor,CompetitionRequest r) {
        actor(actor,true); active(r.sportId()); UUID id=UUID.randomUUID();
        data.insertCompetition(id,r.sportId(),r.name().strip());
        audit.record(actor,"COMPETITION_CREATED","competitions",id); return data.competition(id);
    }
    public CompetitionResponse renameCompetition(UUID actor,UUID id,RenameRequest r) {
        actor(actor,true); data.competition(id); data.renameCompetition(id,r.name().strip());
        audit.record(actor,"COMPETITION_RENAMED","competitions",id); return data.competition(id);
    }
    public Page<SeasonSummary> seasons(UUID actor,UUID sport,UUID competition,UUID season,int page,int size) {
        actor(actor,false); page(page,size); return data.seasons(sport,competition,season,page,size);
    }
    public SeasonResponse season(UUID actor,UUID id) { actor(actor,false); return response(id); }
    public SeasonResponse createSeason(UUID actor,CreateSeasonRequest r) {
        actor(actor,true);
        var competition=data.competition(r.competitionId()); var season=teams.season(r.seasonId()); var format=formats.get(r.matchFormatId());
        active(competition.sportId());
        if (season.archived()) conflict("CONFIGURATION_LOCKED","Sezóna je archivovaná.");
        if (!competition.sportId().equals(format.sportId())) throw ApiException.invalidField("matchFormatId","Formát musí patřit stejnému sportu.");
        if (data.seasonExists(r.competitionId(),r.seasonId())) conflict("DUPLICATE_RESOURCE","Ročník této soutěže pro sezónu již existuje.");
        validator.validate(r.configuration(),format.configuration());
        UUID id=UUID.randomUUID();
        data.insertSeason(id,r.competitionId(),r.seasonId(),r.matchFormatId(),r.configuration());
        data.replaceRules(id,r.configuration());
        audit.record(actor,"COMPETITION_SEASON_CREATED","competition_seasons",id); return response(id);
    }
    public SeasonResponse updateSeason(UUID actor,UUID id,UpdateSeasonRequest r) {
        actor(actor,true); var s=data.season(id); long version=version(r.version(),s); open(s);
        validator.validate(r.configuration(),formats.get(s.matchFormatId()).configuration());
        if (data.updateSeason(id,version,r.configuration())!=1) conflict("OPTIMISTIC_LOCK_CONFLICT","Ročník byl mezitím změněn.");
        data.replaceRules(id,r.configuration());
        audit.record(actor,"COMPETITION_RULES_UPDATED","competition_seasons",id); return response(id);
    }
    public Page<EntryResponse> entries(UUID actor,UUID id,int page,int size) {
        actor(actor,false); page(page,size); data.season(id); return data.entries(id,page,size);
    }
    public SeasonSummary addEntry(UUID actor,UUID id,EntryRequest r) {
        actor(actor,true); var s=data.season(id); long version=version(r.version(),s); open(s); active(s.sportId());
        var team=teams.teamSeason(r.teamSeasonId());
        if (!s.seasonId().equals(team.seasonId()) || !s.sportId().equals(team.sportId())) {
            throw ApiException.invalidField("teamSeasonId","Tým musí patřit stejné sezóně a sportu.");
        }
        if (data.entryExists(id,r.teamSeasonId())) conflict("DUPLICATE_RESOURCE","Tým je již přihlášený.");
        if (data.incrementVersion(id,version)!=1) conflict("OPTIMISTIC_LOCK_CONFLICT","Ročník byl mezitím změněn.");
        data.addEntry(id,r.teamSeasonId());
        audit.recordCompetitionEntry(actor,"COMPETITION_ENTRY_ADDED",id,r.teamSeasonId()); return data.season(id);
    }
    public SeasonSummary removeEntry(UUID actor,UUID id,UUID team,VersionRequest r) {
        actor(actor,true); var s=data.season(id); long version=version(r.version(),s); open(s);
        if (!data.entryExists(id,team)) throw new NoSuchElementException();
        if (data.incrementVersion(id,version)!=1) conflict("OPTIMISTIC_LOCK_CONFLICT","Ročník byl mezitím změněn.");
        data.removeEntry(id,team);
        audit.recordCompetitionEntry(actor,"COMPETITION_ENTRY_REMOVED",id,team); return data.season(id);
    }
}
