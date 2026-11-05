package ru.mephi.hotelbooking.booking;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
class TokenService {

    private final JwtEncoder encoder;

    TokenService(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    String forUser(UserAccount user) {
        return issue(user.id.toString(), user.role, Duration.ofHours(1));
    }

    String forHotelCall() {
        return issue("booking-service", "SERVICE", Duration.ofMinutes(1));
    }

    private String issue(String subject, String role, Duration duration) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("hotel-booking")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(duration))
                .claim("roles", List.of(role))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
