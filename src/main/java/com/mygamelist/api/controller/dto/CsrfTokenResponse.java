package com.mygamelist.api.controller.dto;

import org.springframework.security.web.csrf.CsrfToken;

public record CsrfTokenResponse(String headerName, String parameterName, String token) {

  public static CsrfTokenResponse from(CsrfToken csrfToken) {
    return new CsrfTokenResponse(
        csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken());
  }
}
