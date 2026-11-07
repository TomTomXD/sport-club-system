package cz.jpmad.springprojecttemplate.service.competition;

import cz.jpmad.springprojecttemplate.api.ApiException;
import cz.jpmad.springprojecttemplate.api.competition.dto.MatchFormatDtos.*;
import cz.jpmad.springprojecttemplate.repository.*;
import cz.jpmad.springprojecttemplate.service.user.CurrentAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MatchFormatServiceTests {
    private final ClubRepository clubs = mock(ClubRepository.class);
    private final CurrentAccountService accounts = mock(CurrentAccountService.class);
    private final MatchFormatRepository formats = mock(MatchFormatRepository.class);
    private final SportRepository sports = mock(SportRepository.class);
    private final AuditRepository audit = mock(AuditRepository.class);
    private final CompetitionRepository competitions = mock(CompetitionRepository.class);
    private final CompetitionConfigurationValidator competitionValidator = mock(CompetitionConfigurationValidator.class);
    private final UUID actor = UUID.randomUUID(), id = UUID.randomUUID(), sport = UUID.randomUUID();
    private final Configuration configuration = new Configuration(ScoreMode.SUM_SCORE, true, false, "Poločas", 2, null, false, false, 0, 0, 3);
    private MatchFormatService service;
    @BeforeEach void setup() {
        service = new MatchFormatService(clubs, accounts, formats, sports, audit, competitions, competitionValidator);
        when(formats.get(id)).thenReturn(new Response(id, sport, "Sport", "4", "Name", configuration, Instant.EPOCH));
    }
    @Test void readAndWriteLockBeforeFreshAuthorizationAndDeniedUserDoesNotReachData() {
        service.list(actor, sport, 0, 20);
        var order = inOrder(clubs, accounts, formats);
        order.verify(clubs).lockForRead(); order.verify(accounts).requireAdmin(actor); order.verify(formats).list(sport, 0, 20);
        doThrow(new AccessDeniedException("Denied")).when(accounts).requireAdmin(actor);
        assertThatThrownBy(() -> service.update(actor, id, new UpdateRequest("4", "Rename", configuration))).isInstanceOf(AccessDeniedException.class);
        order.verify(clubs).lockForWrite(); order.verify(accounts).requireAdmin(actor);
        verify(formats, never()).get(id);
    }
    @Test void staleVersionNeverReachesMutationOrAudit() {
        assertThatThrownBy(() -> service.update(actor, id, new UpdateRequest("3", "Name", configuration)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("OPTIMISTIC_LOCK_CONFLICT"));
        verify(formats, never()).update(any(), anyLong(), any(), any()); verifyNoInteractions(audit);
    }
    @Test void usedConfigurationCanOnlyBeRenamedAndWhitespaceIsNormalized() {
        var padded = new Configuration(ScoreMode.SUM_SCORE, true, false, " Poločas ", 2, null, false, false, 0, 0, 3);
        when(formats.update(id, 4, "Renamed", configuration)).thenReturn(1);
        service.update(actor, id, new UpdateRequest("4", " Renamed ", padded));
        verify(formats).update(id, 4, "Renamed", configuration);
        verify(audit).record(actor, "MATCH_FORMAT_UPDATED", "match_formats", id);
    }
    @Test void changingUsedRulesIsRejectedBeforeWrite() {
        var changed = new Configuration(ScoreMode.SUM_SCORE, false, false, "Poločas", 2, null, false, false, 0, 0, 3);
        assertThatThrownBy(() -> service.update(actor, id, new UpdateRequest("4", "Name", changed)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("CONFIGURATION_LOCKED"));
        verify(formats, never()).update(any(), anyLong(), any(), any()); verifyNoInteractions(audit);
    }
    @Test void databaseVersionRangeIsCheckedWithoutOverflow() {
        assertThatThrownBy(() -> service.update(actor, id, new UpdateRequest("9999999999999999999", "Name", configuration)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("VALIDATION_ERROR"));
        when(formats.get(id)).thenReturn(new Response(id, sport, "Sport", Long.toString(Long.MAX_VALUE), "Name", configuration, null));
        assertThatThrownBy(() -> service.update(actor, id, new UpdateRequest(Long.toString(Long.MAX_VALUE), "Name", configuration)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("STATE_CONFLICT"));
        verify(formats, never()).update(any(), anyLong(), any(), any());
    }
}
