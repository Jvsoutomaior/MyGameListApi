package com.mygamelist.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

public class RegistrationRateLimitFilter extends OncePerRequestFilter {

  private static final RequestMatcher REGISTRATION =
      PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/users");
  private static final RequestMatcher RESEND =
      PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/users/resend-code");

  private final RegistrationRateLimiter rateLimiter;
  private final SecurityProblemWriter problemWriter;

  public RegistrationRateLimitFilter(
      RegistrationRateLimiter rateLimiter, SecurityProblemWriter problemWriter) {
    this.rateLimiter = rateLimiter;
    this.problemWriter = problemWriter;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !REGISTRATION.matches(request) && !RESEND.matches(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long retryAfter;
    try {
      retryAfter = rateLimiter.retryAfterSeconds(request.getRemoteAddr());
    } catch (DataAccessException exception) {
      problemWriter.write(
          response,
          HttpStatus.SERVICE_UNAVAILABLE,
          "Registration unavailable",
          "Registration is temporarily unavailable. Please try again later.");
      return;
    }

    if (retryAfter > 0) {
      response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
      problemWriter.write(
          response,
          HttpStatus.TOO_MANY_REQUESTS,
          "Too many registration attempts",
          "Too many registration attempts. Please wait before trying again.");
      return;
    }
    filterChain.doFilter(request, response);
  }
}
