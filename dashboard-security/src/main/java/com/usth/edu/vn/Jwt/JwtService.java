package com.usth.edu.vn.Jwt;

import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Set;
import java.util.UUID;

@Singleton
public class JwtService {

    /** Token lifetime in seconds (default: 1 hour). */
    @ConfigProperty(name = "jwt.lifespan", defaultValue = "3600")
    long lifespan;

    public String generateJwt(String issuer, String subject, Set<String> roles) {
        return Jwt.issuer(issuer)
                .audience(JwtIssuerType.AUDIENCE)
                .subject(subject)
                .groups(roles)
                .claim("jti", UUID.randomUUID().toString())
                .expiresIn(lifespan)
                .innerSign() // default Signature algorithm: RS256 (signing key pair)
                .encrypt(); // default Key encryption algorithm: RSA-OAEP-256 (encryption key pair)
    }
}
