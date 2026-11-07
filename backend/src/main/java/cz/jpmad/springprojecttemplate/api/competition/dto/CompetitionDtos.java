package cz.jpmad.springprojecttemplate.api.competition.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CompetitionDtos {
    private CompetitionDtos() { }
    public record Page<T>(List<T> items,int page,int size,String totalElements,int totalPages) { }
    public enum Outcome { WIN, DRAW, LOSS }
    public enum Ending { REGULATION, OVERTIME, SHOOTOUT }
    public enum Decision { FORFEIT_HOME, FORFEIT_AWAY }
    public enum MissingPolicy { REQUIRE_COMPLETE, PLAYED_ONLY }
    public enum Direction { ASC, DESC }
    public enum Metric {
        POINTS, WINS, SCORE_DIFFERENCE, SCORE_FOR, SCORE_RATIO, PART_SCORE_DIFFERENCE, PART_SCORE_RATIO,
        HEAD_TO_HEAD_POINTS, HEAD_TO_HEAD_SCORE_DIFFERENCE, HEAD_TO_HEAD_SCORE_FOR
    }
    public record CompetitionRequest(@NotNull UUID sportId,@NotBlank @Size(max=160) String name) { }
    public record RenameRequest(@NotBlank @Size(max=160) String name) { }
    public record CompetitionResponse(UUID id,UUID sportId,String sportName,String name) { }
    public record PointRule(@NotNull Outcome outcome,@NotNull Ending ending,
                            @PositiveOrZero Integer ownScoreMin,@PositiveOrZero Integer ownScoreMax,
                            @PositiveOrZero Integer opponentScoreMin,@PositiveOrZero Integer opponentScoreMax,
                            @NotNull @PositiveOrZero Integer points) { }
    public record ForfeitRule(@NotNull Decision decision,@NotNull @PositiveOrZero Integer homeScore,
                              @NotNull @PositiveOrZero Integer awayScore,@NotNull @PositiveOrZero Integer homePoints,
                              @NotNull @PositiveOrZero Integer awayPoints) { }
    public record RankingCriterion(@NotNull @Positive Integer priority,@NotNull Metric metric,@NotNull Direction direction) { }
    public record Configuration(@NotNull MissingPolicy headToHeadMissingPolicy,
                                @NotNull @Positive Integer headToHeadMeetingsPerPair,
                                @NotNull Boolean restartHeadToHeadOnSubgroup,
                                @NotNull @Size(min=2,max=100) List<@NotNull @Valid PointRule> pointRules,
                                @NotNull @Size(min=2,max=2) List<@NotNull @Valid ForfeitRule> forfeitRules,
                                @NotNull @Size(min=1,max=10) List<@NotNull @Valid RankingCriterion> rankingCriteria) { }
    public record CreateSeasonRequest(@NotNull UUID competitionId,@NotNull UUID seasonId,@NotNull UUID matchFormatId,
                                       @NotNull @Valid Configuration configuration) { }
    public record UpdateSeasonRequest(@NotNull @Pattern(regexp="0|[1-9][0-9]{0,18}") String version,
                                       @NotNull @Valid Configuration configuration) { }
    public record EntryRequest(@NotNull @Pattern(regexp="0|[1-9][0-9]{0,18}") String version,@NotNull UUID teamSeasonId) { }
    public record VersionRequest(@NotNull @Pattern(regexp="0|[1-9][0-9]{0,18}") String version) { }
    public record SeasonSummary(UUID id,UUID competitionId,String competitionName,UUID seasonId,String seasonName,
                                UUID sportId,String sportName,UUID matchFormatId,String matchFormatName,
                                String version,String resultsRevision,Instant lockedAt,boolean archived) { }
    public record SeasonResponse(SeasonSummary season,Configuration configuration) { }
    public record EntryResponse(UUID teamSeasonId,String displayName,boolean isClubTeam) { }
}
