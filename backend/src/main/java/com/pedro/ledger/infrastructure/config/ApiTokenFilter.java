package com.pedro.ledger.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class ApiTokenFilter extends OncePerRequestFilter {

  private static final String BEARER = "Bearer ";
  private final byte[] expectedToken;

  ApiTokenFilter(Environment environment) {
    String token = environment.getProperty("FINORA_API_TOKEN", "");
    boolean required = environment.matchesProfiles("neon")
        || environment.getProperty("RENDER", Boolean.class, false);
    if ((required || !token.isEmpty()) && !token.matches("[0-9a-fA-F]{64}")) {
      throw new IllegalStateException("FINORA_API_TOKEN must contain 64 hexadecimal characters");
    }
    expectedToken = token.getBytes(StandardCharsets.US_ASCII);
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return expectedToken.length == 0 || "OPTIONS".equals(request.getMethod());
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header == null || header.length() != BEARER.length() + expectedToken.length
        || !header.startsWith(BEARER)
        || !MessageDigest.isEqual(expectedToken, header.substring(BEARER.length())
            .getBytes(StandardCharsets.UTF_8))) {
      response.setHeader("WWW-Authenticate", "Bearer");
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    chain.doFilter(request, response);
  }
}
