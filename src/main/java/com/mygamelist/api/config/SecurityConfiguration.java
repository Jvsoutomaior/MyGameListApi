package com.mygamelist.api.config;

import com.mygamelist.api.repository.UserAccountRepository;
import com.mygamelist.api.security.AuthenticatedUser;
import jakarta.servlet.DispatcherType;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      SecurityContextRepository securityContextRepository,
      CsrfTokenRepository csrfTokenRepository)
      throws Exception {
    http.cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository).spa())
        .securityContext(
            securityContext -> securityContext.securityContextRepository(securityContextRepository))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .dispatcherTypeMatchers(DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/auth/csrf")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/users", "/api/auth/login")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .requestCache(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(
                    (request, response, exception) ->
                        response.sendError(HttpStatus.UNAUTHORIZED.value())))
        .logout(
            logout ->
                logout
                    .logoutUrl("/api/auth/logout")
                    .logoutSuccessHandler(
                        (request, response, authentication) -> response.setStatus(204)));

    return http.build();
  }

  @Bean
  UserDetailsService userDetailsService(UserAccountRepository userAccountRepository) {
    return email ->
        userAccountRepository
            .findByEmail(email.trim().toLowerCase(Locale.ROOT))
            .map(AuthenticatedUser::from)
            .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
      throws Exception {
    return configuration.getAuthenticationManager();
  }

  @Bean
  SecurityContextRepository securityContextRepository() {
    return new HttpSessionSecurityContextRepository();
  }

  @Bean
  CsrfTokenRepository csrfTokenRepository() {
    return CookieCsrfTokenRepository.withHttpOnlyFalse();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${app.cors.allowed-origin:http://localhost:5173}") String allowedOrigin) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of(allowedOrigin));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
    configuration.setExposedHeaders(List.of("Location"));
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }
}
