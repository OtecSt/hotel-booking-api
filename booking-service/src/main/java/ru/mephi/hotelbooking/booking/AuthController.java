package ru.mephi.hotelbooking.booking;

import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AccountService accounts;
    private final TokenService tokens;

    AuthController(AccountService accounts, TokenService tokens) {
        this.accounts = accounts;
        this.tokens = tokens;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    TokenResponse register(@Valid @RequestBody Credentials credentials) {
        return new TokenResponse(tokens.forUser(
                accounts.register(credentials.username(), credentials.password(), AccountService.Role.USER)));
    }

    @PostMapping("/login")
    @SecurityRequirements
    TokenResponse login(@Valid @RequestBody Credentials credentials) {
        return new TokenResponse(tokens.forUser(accounts.authenticate(credentials.username(), credentials.password())));
    }

    record Credentials(@NotBlank @Size(max = 64) String username, @NotBlank @Size(min = 8) String password) {
    }

    record TokenResponse(String accessToken) {
    }
}
