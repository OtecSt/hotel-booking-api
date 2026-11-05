package ru.mephi.hotelbooking.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final UserAccountRepository users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;

    AuthController(UserAccountRepository users, PasswordEncoder passwords, TokenService tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    TokenResponse register(@Valid @RequestBody Credentials credentials) {
        validatePassword(credentials.password());
        UserAccount user;
        try {
            user = users.save(new UserAccount(credentials.username(), passwords.encode(credentials.password())));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already registered", exception);
        }
        return new TokenResponse(tokens.forUser(user));
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody Credentials credentials) {
        validatePassword(credentials.password());
        UserAccount user = users.findByUsername(credentials.username()).orElse(null);
        if (user == null || !passwords.matches(credentials.password(), user.password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        return new TokenResponse(tokens.forUser(user));
    }

    private void validatePassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain at most 72 UTF-8 bytes");
        }
    }

    record Credentials(@NotBlank @Size(max = 64) String username, @NotBlank @Size(min = 8) String password) {
    }

    record TokenResponse(String accessToken) {
    }
}
