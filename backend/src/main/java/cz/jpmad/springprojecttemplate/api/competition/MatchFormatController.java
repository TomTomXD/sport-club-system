package cz.jpmad.springprojecttemplate.api.competition;

import cz.jpmad.springprojecttemplate.api.competition.dto.MatchFormatDtos.*;
import cz.jpmad.springprojecttemplate.model.user.User;
import cz.jpmad.springprojecttemplate.service.competition.MatchFormatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/match-formats")
@RequiredArgsConstructor
public class MatchFormatController {
    private final MatchFormatService formats;
    @GetMapping
    public Page list(@AuthenticationPrincipal User user, @RequestParam(required = false) UUID sportId,
                     @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return formats.list(user.getId(), sportId, page, size);
    }
    @GetMapping("/{id}")
    public Response get(@AuthenticationPrincipal User user, @PathVariable UUID id) { return formats.get(user.getId(), id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Response create(@AuthenticationPrincipal User user, @Valid @RequestBody CreateRequest request) {
        return formats.create(user.getId(), request);
    }
    @PutMapping("/{id}")
    public Response update(@AuthenticationPrincipal User user, @PathVariable UUID id, @Valid @RequestBody UpdateRequest request) {
        return formats.update(user.getId(), id, request);
    }
}
