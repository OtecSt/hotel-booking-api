package ru.mephi.hotelbooking.booking;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
class AccountController {

    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    UserPage list(@RequestParam(defaultValue = "0") @Min(0) int page,
                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var result = accounts.list(PageRequest.of(page, size, Sort.by("username")));
        return new UserPage(result.getContent(), page, size, result.getTotalElements());
    }

    @GetMapping("/{id}")
    AccountService.UserView get(@PathVariable UUID id) {
        return accounts.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AccountService.UserView create(@Valid @RequestBody NewUser input) {
        return AccountService.UserView.from(accounts.register(input.username(), input.password(), input.role()));
    }

    @PatchMapping("/{id}")
    AccountService.UserView update(@PathVariable UUID id, @Valid @RequestBody UserPatch input,
                                   @Parameter(hidden = true) @AuthenticationPrincipal Jwt actor) {
        return accounts.update(id, UUID.fromString(actor.getSubject()), input.username(), input.password(), input.role());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt actor) {
        accounts.delete(id, UUID.fromString(actor.getSubject()));
    }

    record NewUser(@NotBlank @Size(max = 64) String username, @NotBlank String password,
                   @NotNull AccountService.Role role) {
    }

    record UserPatch(@Size(max = 64) @Pattern(regexp = ".*\\S.*") String username,
                     String password, AccountService.Role role) {
    }

    record UserPage(List<AccountService.UserView> items, int page, int size, long total) {
    }
}
