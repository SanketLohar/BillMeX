package com.billme.payment;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Service
public class RefundTokenService {

    @Value("${jwt.secret}")
    private String secret;

    private Key key;

    private static final long REFUND_TOKEN_EXPIRATION = 1000 * 60 * 60 * 48; // 48 hours

    @PostConstruct
    public void init() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters long");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateRefundToken(Long invoiceId, Long merchantId) {
        return Jwts.builder()
                .setSubject(invoiceId.toString())
                .claim("merchantId", merchantId.toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + REFUND_TOKEN_EXPIRATION))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Long extractInvoiceId(String token) {
        return Long.parseLong(extractAllClaims(token).getSubject());
    }

    public Long extractMerchantId(String token) {
        return Long.parseLong(extractAllClaims(token).get("merchantId", String.class));
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
