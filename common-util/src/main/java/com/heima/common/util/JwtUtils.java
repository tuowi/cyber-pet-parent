package com.heima.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 创建/解析/校验工具
 */
@Component
public class JwtUtils {

    /**
     * 创建 JWT
     *
     * @param userId 用户ID
     * @param claims 额外声明
     * @return JWT 字符串
     */
    public String createToken(Long userId, String secret, long expiration, Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        JwtBuilder builder = Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expiration));

        if (claims != null && !claims.isEmpty()) {
            builder.addClaims(claims);
        }

        SecretKey secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return builder.signWith(secretKey, SignatureAlgorithm.HS256).compact();
    }

    /**
     * 解析 JWT 并返回 Claims
     *
     * @param token JWT 字符串
     * @return Claims，解析失败返回 null
     */
    public Claims parseToken(String token, String secret) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (JwtException e) {
            return null;
        }
    }

    /**
     * 从 Token 中获取 userId
     */
    public Long getUserId(String token, String secret) {
        Claims claims = parseToken(token, secret);
        if (claims == null) {
            return null;
        }
        return Long.valueOf(claims.getSubject());
    }

    /**
     * 校验 Token 是否有效（未过期且签名正确）
     */
    public boolean validateToken(String token, String secret) {
        Claims claims = parseToken(token, secret);
        if (claims == null) {
            return false;
        }
        return !claims.getExpiration().before(new Date());
    }
}
