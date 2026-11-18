package com.secmulher.modules.core.infra.security;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.secmulher.modules.core.infra.entities.User;

@Service
public class TokenService {
    public static final String ISSUER = "omnibase";

    final JwtEncoder jwtEncoder;
    final Duration expiration;

    public TokenService(JwtEncoder jwtEncoder, @Value("${security.jwt.expiration:8h}") Duration expiration) {
        this.jwtEncoder = jwtEncoder;
        this.expiration = expiration;
    }

    public record IssuedToken(String token, Instant expiresAt) {}

    // O token só carrega a identidade; permissões são consultadas no banco a cada requisição,
    // então mudanças de acesso valem na hora, sem esperar o token expirar
    public IssuedToken issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(expiration);

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(user.getId().toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .claim("name", user.getName())
            .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
