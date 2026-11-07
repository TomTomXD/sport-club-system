package cz.jpmad.springprojecttemplate.service.competition;

import cz.jpmad.springprojecttemplate.api.ApiException;
import cz.jpmad.springprojecttemplate.api.competition.dto.CompetitionDtos.*;
import cz.jpmad.springprojecttemplate.repository.*;
import cz.jpmad.springprojecttemplate.service.user.CurrentAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompetitionServiceTests {
    private final ClubRepository clubs=mock(ClubRepository.class);
    private final CurrentAccountService accounts=mock(CurrentAccountService.class);
    private final CompetitionRepository data=mock(CompetitionRepository.class);
    private final MatchFormatRepository formats=mock(MatchFormatRepository.class);
    private final TeamDomainRepository teams=mock(TeamDomainRepository.class);
    private final CompetitionConfigurationValidator validator=mock(CompetitionConfigurationValidator.class);
    private final AuditRepository audit=mock(AuditRepository.class);
    private final CompetitionService service=new CompetitionService(clubs,accounts,data,formats,teams,validator,audit);
    private final UUID actor=UUID.randomUUID(),id=UUID.randomUUID();
    @Test void readAndWriteAcquireClubLockBeforeFreshAdminCheckAndDenialNeverReadsDomainData() {
        doThrow(new AccessDeniedException("Denied")).when(accounts).requireAdmin(actor);
        assertThatThrownBy(()->service.competitions(actor,null,0,20)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.createSeason(actor,new CreateSeasonRequest(id,id,id,null))).isInstanceOf(AccessDeniedException.class);
        var order=inOrder(clubs,accounts);order.verify(clubs).lockForRead();order.verify(accounts).requireAdmin(actor);
        order.verify(clubs).lockForWrite();order.verify(accounts).requireAdmin(actor);
        verifyNoInteractions(data,formats,teams,validator,audit);
    }
    @Test void staleOrLockedSeasonCannotReachConfigurationOrEntryMutation() {
        when(data.season(id)).thenReturn(new SeasonSummary(id,id,"Competition",id,"Season",id,"Sport",id,"Format","8","0",Instant.EPOCH,false));
        assertThatThrownBy(()->service.updateSeason(actor,id,new UpdateSeasonRequest("7",null))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("OPTIMISTIC_LOCK_CONFLICT"));
        assertThatThrownBy(()->service.addEntry(actor,id,new EntryRequest("8",id))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("CONFIGURATION_LOCKED"));
        verifyNoInteractions(formats,teams,validator,audit);verify(data,never()).replaceRules(any(),any());verify(data,never()).addEntry(any(),any());
    }
}
