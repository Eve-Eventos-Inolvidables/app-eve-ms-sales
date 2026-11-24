package com.example.appevemssales.Service.Security;

import com.example.appevemssales.Service.Buy.InvalidAuthenticatedUserException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class AuthenticatedUserIdResolver {

    private final String userIdClaim;

    public AuthenticatedUserIdResolver(
            @Value("${jwt.claim.user:id}") String userIdClaim) {
        this.userIdClaim = userIdClaim;
    }

    public Long resolve(Jwt jwt) {
        Object claim = jwt == null ? null : jwt.getClaim(userIdClaim);
        if (!(claim instanceof Number number)) {
            throw new InvalidAuthenticatedUserException();
        }

        try {
            long userId = new BigDecimal(number.toString()).longValueExact();
            if (userId > 0) {
                return userId;
            }
        } catch (NumberFormatException | ArithmeticException ignored) {
            // A missing, fractional or out-of-range user ID is not a valid identity.
        }
        throw new InvalidAuthenticatedUserException();
    }

    public Long resolveCurrent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new InvalidAuthenticatedUserException();
        }
        return resolve(jwtAuthentication.getToken());
    }
}
