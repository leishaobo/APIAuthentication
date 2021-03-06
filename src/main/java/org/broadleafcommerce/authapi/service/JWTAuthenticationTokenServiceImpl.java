/*-
 * #%L
 * BroadleafCommerce API Authentication
 * %%
 * Copyright (C) 2009 - 2017 Broadleaf Commerce
 * %%
 * Licensed under the Broadleaf Fair Use License Agreement, Version 1.0
 * (the "Fair Use License" located  at http://license.broadleafcommerce.org/fair_use_license-1.0.txt)
 * unless the restrictions on use therein are violated and require payment to Broadleaf in which case
 * the Broadleaf End User License Agreement (EULA), Version 1.1
 * (the "Commercial License" located at http://license.broadleafcommerce.org/commercial_license-1.1.txt)
 * shall apply.
 * 
 * Alternatively, the Commercial License may be replaced with a mutually agreed upon license (the "Custom License")
 * between you and Broadleaf Commerce. You may not use this file except in compliance with the applicable license.
 * #L%
 */
package org.broadleafcommerce.authapi.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;

import org.broadleafcommerce.authapi.exception.ExpiredAuthenticationTokenException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.broadleafcommerce.authapi.domain.ApiUserDTO;
import java.util.Date;
import javax.servlet.http.Cookie;

/**
 * @author Nick Crum ncrum
 */
@Service("blJWTTokenService")
@ConditionalOnProperty(name = "blc.auth.jwt.enabled")
public class JWTAuthenticationTokenServiceImpl implements AuthenticationTokenService {

    protected final Environment environment;

    @Autowired
    public JWTAuthenticationTokenServiceImpl(Environment environment) {
        this.environment = environment;
    }

    @Override
    public ApiUserDTO parseAccessToken(String token) throws ExpiredJwtException {
        return parseTokenUsingSecret(token, getAuthenticationSecret());
    }

    @Override
    public ApiUserDTO parseRefreshToken(String token) throws ExpiredJwtException {
        return parseTokenUsingSecret(token, getRefreshSecret());
    }

    protected ApiUserDTO parseTokenUsingSecret(String token, String secret) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(secret)
                    .parseClaimsJws(token)
                    .getBody();

            ApiUserDTO dto = new ApiUserDTO();
            dto.setUsername(claims.getSubject());
            dto.setUserId(claims.get("userId", Integer.class).longValue());
            dto.setCrossAppAuth(claims.get("isCrossAppAuth", Boolean.class));
            dto.setRole(claims.get("role", String.class));

            return dto;
        } catch (UnsupportedJwtException | SignatureException | IllegalArgumentException | MalformedJwtException e) {
            return null;
        } catch (ExpiredJwtException e) {
            throw new ExpiredAuthenticationTokenException(e);
        }
    }

    /**
     * This method is responsible for taking the token subject and generating the correct JWT authentication token.
     * @return the JWT token
     */
    @Override
    public String generateAuthenticationToken(Long userId, String username, boolean isCrossAppAuth, String commaSeparatedAuthorities) {
        Claims claims = Jwts.claims()
                .setSubject(username)
                .setExpiration(new Date(System.currentTimeMillis() + getAuthenticationTokenExpirationTime()));
        claims.put("userId", userId);
        claims.put("isCrossAppAuth", isCrossAppAuth);
        if (commaSeparatedAuthorities != null) {
            claims.put("role", commaSeparatedAuthorities);
        }
        return Jwts.builder()
                .setClaims(claims)
                .signWith(SignatureAlgorithm.HS512, getAuthenticationSecret())
                .compact();
    }

    @Override
    public String generateRefreshToken(Long userId, String username, boolean isCrossAppAuth, String commaSeparatedAuthorities) {
        Claims claims = Jwts.claims()
                .setSubject(username)
                .setExpiration(new Date(System.currentTimeMillis() + getRefreshTokenExpirationTime()));
        claims.put("userId", userId);
        claims.put("isCrossAppAuth", isCrossAppAuth);
        if (commaSeparatedAuthorities != null) {
            claims.put("role", commaSeparatedAuthorities);
        }
        return Jwts.builder()
                .setClaims(claims)
                .signWith(SignatureAlgorithm.HS512, getRefreshSecret())
                .compact();
    }

    @Override
    public Long parseCustomerToken(String customerToken) {
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(getCustomerSecret())
                    .parseClaimsJws(customerToken)
                    .getBody();
            return Long.valueOf(claims.getSubject());
        } catch (ExpiredJwtException | UnsupportedJwtException | SignatureException | IllegalArgumentException | MalformedJwtException e) {
            return null;
        }
    }

    @Override
    public String generateCustomerToken(Long customerId) {
        Claims claims = Jwts.claims()
                .setSubject(String.valueOf(customerId))
                .setExpiration(new Date(System.currentTimeMillis() + getCustomerTokenExpirationTime()));
        return Jwts.builder()
                .setClaims(claims)
                .signWith(SignatureAlgorithm.HS512, getCustomerSecret())
                .compact();
    }

    @Override
    public Cookie buildRefreshTokenCookie(String refreshToken) {
        Cookie cookie = new Cookie(getRefreshTokenCookieName(), refreshToken);
        cookie.setPath("/");
        cookie.setMaxAge(getRefreshTokenCookieMaxAge());
        cookie.setHttpOnly(true);
        cookie.setSecure(isRefreshTokenSecure());
        return cookie;
    }

    protected String getAuthenticationSecret() {
        return environment.getProperty("blc.auth.jwt.access.secret");
    }

    protected Long getRefreshTokenExpirationTime() {
        return environment.getProperty("blc.auth.jwt.refresh.expiration", Long.class);
    }

    protected String getRefreshSecret() {
        return environment.getProperty("blc.auth.jwt.refresh.secret");
    }

    protected String getRefreshTokenCookieName() {
        return environment.getProperty("blc.auth.jwt.refresh.cookie.name");
    }

    protected int getRefreshTokenCookieMaxAge() {
        return environment.getProperty("blc.auth.jwt.refresh.cookie.expiration", Integer.class);
    }

    protected boolean isRefreshTokenSecure() {
        return environment.getProperty("blc.auth.jwt.refresh.cookie.secure", Boolean.class);
    }

    protected Long getAuthenticationTokenExpirationTime() {
        return environment.getProperty("blc.auth.jwt.access.expiration", Long.class);
    }

    protected Long getCustomerTokenExpirationTime() {
        return environment.getProperty("blc.auth.jwt.customer.expiration", Long.class);
    }

    protected String getCustomerSecret() {
        return environment.getProperty("blc.auth.jwt.customer.secret");
    }


}
