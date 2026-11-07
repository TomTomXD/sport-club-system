package cz.jpmad.springprojecttemplate.api.competition.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MatchFormatDtos {
    private MatchFormatDtos() { }
    public enum ScoreMode { SUM_SCORE, WON_PARTS }
    public record Configuration(
            @NotNull ScoreMode scoreMode,
            @NotNull Boolean allowDraw,
            @NotNull Boolean periodsRequired,
            @NotBlank @Size(max = 100) String periodLabel,
            @Positive Integer regulationPeriodCount,
            @Positive Integer winsRequired,
            @NotNull Boolean allowOvertime,
            @NotNull Boolean allowShootout,
            @NotNull @PositiveOrZero Integer shootoutWinnerBonus,
            @NotNull @PositiveOrZero Integer friendlyForfeitLoserScore,
            @NotNull @Positive Integer friendlyForfeitWinnerScore
    ) { }
    public record CreateRequest(
            @NotNull UUID sportId,
            @NotBlank @Size(max = 120) String name,
            @NotNull @Valid Configuration configuration
    ) { }
    public record UpdateRequest(
            @NotNull @Pattern(regexp = "0|[1-9][0-9]{0,18}") String version,
            @NotBlank @Size(max = 120) String name,
            @NotNull @Valid Configuration configuration
    ) { }
    public record Response(UUID id, UUID sportId, String sportName, String version, String name,
                           Configuration configuration, Instant lockedAt) { }
    public record Page(List<Response> items, int page, int size, String totalElements, int totalPages) { }
}
