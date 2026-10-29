package dev.automata.automata.security;

import dev.automata.automata.model.Device;
import dev.automata.automata.model.Users;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${application.security.jwt.secret-key}")
    private String secretKey;
    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;
    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshExpiration;

    /**
     * The signing key and the parser built from it. Both are immutable and thread-safe, so they are built once
     * instead of on every verification (previously: Base64-decode + new key + new parser, three times per request).
     * Held in one record behind a volatile so another thread can never observe a key without its parser.
     */
    private record Crypto(SecretKey key, JwtParser parser) {
    }

    private volatile Crypto crypto;

    private Crypto crypto() {
        Crypto c = crypto;
        if (c == null) {   // benign race: two threads may both build it; the results are identical
            SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
            c = new Crypto(key, Jwts.parser().verifyWith(key).build());
            crypto = c;
        }
        return c;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        Users user = (Users) userDetails;
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());   // embed userId
        claims.put("role", user.getRole().name());
        return generateToken(claims, userDetails);
    }

    public String generateDeviceToken(Device device) {

        Map<String, Object> claims = new HashMap<>();

        claims.put("deviceId", device.getId());
        claims.put("type", "DEVICE");

        return Jwts.builder()
                .claims(claims)
                .subject(device.getMacAddr())
                .issuedAt(new Date())
                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + Duration.ofDays(90).toMillis()
                        )
                )
                .signWith(getSignInKey())
                .compact();
    }

    public String extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", String.class));
    }

    public String generateToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails
    ) {
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    public String generateRefreshToken(
            UserDetails userDetails
    ) {
        return buildToken(new HashMap<>(), userDetails, refreshExpiration);
    }

    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expiration
    ) {
        return Jwts
                .builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey())
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        return isTokenValid(parseClaims(token), userDetails);
    }

    /**
     * Verifies the signature and expiry once and returns the claims. Callers that need several claims, or both the
     * subject and the validity check, should parse once and reuse this instead of calling extract*() repeatedly.
     */
    public Claims parseClaims(String token) {
        return extractAllClaims(token);
    }

    public boolean isTokenValid(Claims claims, UserDetails userDetails) {
        return claims.getSubject() != null
                && claims.getSubject().equals(userDetails.getUsername())
                && claims.getExpiration() != null
                && !claims.getExpiration().before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return crypto().parser().parseSignedClaims(token).getPayload();
    }

    private SecretKey getSignInKey() {
        return crypto().key();
    }
}