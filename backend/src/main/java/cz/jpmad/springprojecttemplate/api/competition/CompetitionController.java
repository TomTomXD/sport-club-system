package cz.jpmad.springprojecttemplate.api.competition;

import cz.jpmad.springprojecttemplate.api.competition.dto.CompetitionDtos.*;
import cz.jpmad.springprojecttemplate.model.user.User;
import cz.jpmad.springprojecttemplate.service.competition.CompetitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CompetitionController {
    private final CompetitionService service;
    @GetMapping("/competitions")
    public Page<CompetitionResponse> competitions(@AuthenticationPrincipal User u,@RequestParam(required=false) UUID sportId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.competitions(u.getId(),sportId,page,size); }
    @GetMapping("/competitions/{id}")
    public CompetitionResponse competition(@AuthenticationPrincipal User u,@PathVariable UUID id) { return service.competition(u.getId(),id); }
    @PostMapping("/competitions") @ResponseStatus(HttpStatus.CREATED)
    public CompetitionResponse createCompetition(@AuthenticationPrincipal User u,@Valid @RequestBody CompetitionRequest r) { return service.createCompetition(u.getId(),r); }
    @PutMapping("/competitions/{id}")
    public CompetitionResponse renameCompetition(@AuthenticationPrincipal User u,@PathVariable UUID id,@Valid @RequestBody RenameRequest r) { return service.renameCompetition(u.getId(),id,r); }
    @GetMapping("/competition-seasons")
    public Page<SeasonSummary> seasons(@AuthenticationPrincipal User u,@RequestParam(required=false) UUID sportId,@RequestParam(required=false) UUID competitionId,
            @RequestParam(required=false) UUID seasonId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.seasons(u.getId(),sportId,competitionId,seasonId,page,size); }
    @GetMapping("/competition-seasons/{id}")
    public SeasonResponse season(@AuthenticationPrincipal User u,@PathVariable UUID id) { return service.season(u.getId(),id); }
    @PostMapping("/competition-seasons") @ResponseStatus(HttpStatus.CREATED)
    public SeasonResponse createSeason(@AuthenticationPrincipal User u,@Valid @RequestBody CreateSeasonRequest r) { return service.createSeason(u.getId(),r); }
    @PutMapping("/competition-seasons/{id}")
    public SeasonResponse updateSeason(@AuthenticationPrincipal User u,@PathVariable UUID id,@Valid @RequestBody UpdateSeasonRequest r) { return service.updateSeason(u.getId(),id,r); }
    @GetMapping("/competition-seasons/{id}/point-rules")
    public List<PointRule> points(@AuthenticationPrincipal User u,@PathVariable UUID id) { return service.season(u.getId(),id).configuration().pointRules(); }
    @GetMapping("/competition-seasons/{id}/forfeit-rules")
    public List<ForfeitRule> forfeits(@AuthenticationPrincipal User u,@PathVariable UUID id) { return service.season(u.getId(),id).configuration().forfeitRules(); }
    @GetMapping("/competition-seasons/{id}/ranking-criteria")
    public List<RankingCriterion> ranking(@AuthenticationPrincipal User u,@PathVariable UUID id) { return service.season(u.getId(),id).configuration().rankingCriteria(); }
    @GetMapping("/competition-seasons/{id}/entries")
    public Page<EntryResponse> entries(@AuthenticationPrincipal User u,@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.entries(u.getId(),id,page,size); }
    @PostMapping("/competition-seasons/{id}/entries")
    public SeasonSummary addEntry(@AuthenticationPrincipal User u,@PathVariable UUID id,@Valid @RequestBody EntryRequest r) { return service.addEntry(u.getId(),id,r); }
    @DeleteMapping("/competition-seasons/{id}/entries/{team}")
    public SeasonSummary removeEntry(@AuthenticationPrincipal User u,@PathVariable UUID id,@PathVariable UUID team,@Valid @RequestBody VersionRequest r) { return service.removeEntry(u.getId(),id,team,r); }
}
