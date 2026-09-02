package com.mygamelist.api.security;

import com.mygamelist.api.entity.UserAccount;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class AuthenticatedUser implements UserDetails {

  @Serial private static final long serialVersionUID = 1L;

  private static final List<GrantedAuthority> AUTHORITIES =
      List.of(new SimpleGrantedAuthority("ROLE_USER"));

  private final UUID id;
  private final String username;
  private final String email;
  private final String passwordHash;

  private AuthenticatedUser(UUID id, String username, String email, String passwordHash) {
    this.id = id;
    this.username = username;
    this.email = email;
    this.passwordHash = passwordHash;
  }

  public static AuthenticatedUser from(UserAccount user) {
    return new AuthenticatedUser(
        user.getId(), user.getUsername(), user.getEmail(), user.getPasswordHash());
  }

  public UUID id() {
    return id;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return AUTHORITIES;
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return email;
  }

  @Override
  public String toString() {
    return "AuthenticatedUser[id=" + id + ", username=" + username + "]";
  }
}
