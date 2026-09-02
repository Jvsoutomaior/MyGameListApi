package com.mygamelist.api.controller;

import com.mygamelist.api.controller.dto.CsrfTokenResponse;
import com.mygamelist.api.controller.dto.LoginRequest;
import com.mygamelist.api.controller.dto.UserResponse;
import com.mygamelist.api.security.AuthenticatedUser;
import com.mygamelist.api.service.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository;
  private final CsrfTokenRepository csrfTokenRepository;
  private final UserAccountService userAccountService;
  private final SecurityContextHolderStrategy securityContextHolderStrategy =
      SecurityContextHolder.getContextHolderStrategy();

  public AuthenticationController(
      AuthenticationManager authenticationManager,
      SecurityContextRepository securityContextRepository,
      CsrfTokenRepository csrfTokenRepository,
      UserAccountService userAccountService) {
    this.authenticationManager = authenticationManager;
    this.securityContextRepository = securityContextRepository;
    this.csrfTokenRepository = csrfTokenRepository;
    this.userAccountService = userAccountService;
  }

  @GetMapping("/csrf")
  public CsrfTokenResponse csrf(CsrfToken csrfToken) {
    return CsrfTokenResponse.from(csrfToken);
  }

  @PostMapping("/login")
  public UserResponse login(
      @Valid @RequestBody LoginRequest loginRequest,
      HttpServletRequest request,
      HttpServletResponse response) {
    Authentication authentication =
        authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken.unauthenticated(
                loginRequest.email(), loginRequest.password()));
    AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
    UserResponse user = UserResponse.from(userAccountService.findById(principal.id()));

    if (request.getSession(false) != null) {
      request.changeSessionId();
    }

    SecurityContext context = securityContextHolderStrategy.createEmptyContext();
    context.setAuthentication(authentication);
    securityContextHolderStrategy.setContext(context);
    securityContextRepository.saveContext(context, request, response);
    csrfTokenRepository.saveToken(null, request, response);
    return user;
  }

  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
    return UserResponse.from(userAccountService.findById(principal.id()));
  }
}
