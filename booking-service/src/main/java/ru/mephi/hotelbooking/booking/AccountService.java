package ru.mephi.hotelbooking.booking;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class AccountService {

    private final UserAccountRepository users;
    private final BookingRepository bookings;
    private final PasswordEncoder passwords;

    AccountService(UserAccountRepository users, BookingRepository bookings, PasswordEncoder passwords) {
        this.users = users;
        this.bookings = bookings;
        this.passwords = passwords;
    }

    UserAccount register(String username, String password, Role role) {
        var user = new UserAccount(username, encode(password));
        user.role = role.name();
        return users.save(user);
    }

    UserAccount authenticate(String username, String password) {
        validatePassword(password);
        UserAccount user = users.findByUsername(username).orElse(null);
        if (user == null || !passwords.matches(password, user.password)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        return user;
    }

    Page<UserView> list(Pageable pageable) {
        return users.findAll(pageable).map(UserView::from);
    }

    UserView get(UUID id) {
        return users.findById(id).map(UserView::from).orElseThrow(AccountService::notFound);
    }

    @Transactional
    public UserView update(UUID id, UUID actor, String username, String password, Role role) {
        UserAccount user = lock(id);
        if (id.equals(actor) && role == Role.USER) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot remove your own admin role");
        }
        if (username != null) {
            user.username = username;
        }
        if (password != null) {
            user.password = encode(password);
        }
        if (role != null) {
            user.role = role.name();
        }
        return UserView.from(user);
    }

    @Transactional
    public void delete(UUID id, UUID actor) {
        UserAccount user = lock(id);
        if (id.equals(actor) || bookings.existsByUserId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete your account or an account with booking history");
        }
        users.delete(user);
    }

    private UserAccount lock(UUID id) {
        return users.lockById(id).orElseThrow(AccountService::notFound);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
    }

    private String encode(String password) {
        validatePassword(password);
        return passwords.encode(password);
    }

    private void validatePassword(String password) {
        int length = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.length() < 8 || password.isBlank() || length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must have at least 8 characters and at most 72 UTF-8 bytes");
        }
    }

    enum Role {
        USER, ADMIN
    }

    record UserView(UUID id, String username, String role) {
        static UserView from(UserAccount user) {
            return new UserView(user.id, user.username, user.role);
        }
    }
}
