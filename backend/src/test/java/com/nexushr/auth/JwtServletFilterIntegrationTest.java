package com.nexushr.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:nexushr_filter;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "app.security.jwt-secret=01234567890123456789012345678901",
      "app.bootstrap.admin-email=admin-filter@nexushr.local",
      "app.bootstrap.admin-password=StrongAdminPassword123!"
    })
class JwtServletFilterIntegrationTest {
  @LocalServerPort int port;
  @Autowired TestRestTemplate rest;

  @Test
  void accessTokenFromLoginAuthenticatesAgainstEmbeddedServer() {
    String baseUrl = "http://localhost:" + port;
    HttpHeaders loginHeaders = new HttpHeaders();
    loginHeaders.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<JsonNode> login = rest.exchange(baseUrl + "/api/auth/login", HttpMethod.POST,
        new HttpEntity<>("{\"email\":\"admin-filter@nexushr.local\",\"password\":\"StrongAdminPassword123!\"}", loginHeaders), JsonNode.class);
    assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertAuthenticatedMe(baseUrl, login.getBody().path("accessToken").asText());

    String cookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    assertThat(cookie).isNotBlank();
    HttpHeaders refreshHeaders = new HttpHeaders();
    refreshHeaders.set(HttpHeaders.COOKIE, cookie.split(";", 2)[0]);
    ResponseEntity<JsonNode> refreshed = rest.exchange(baseUrl + "/api/auth/refresh", HttpMethod.POST,
        new HttpEntity<>(refreshHeaders), JsonNode.class);
    assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertAuthenticatedMe(baseUrl, refreshed.getBody().path("accessToken").asText());
  }

  private void assertAuthenticatedMe(String baseUrl, String accessToken) {
    assertThat(accessToken).isNotBlank();
    HttpHeaders protectedHeaders = new HttpHeaders();
    protectedHeaders.setBearerAuth(accessToken);
    ResponseEntity<JsonNode> me = rest.exchange(baseUrl + "/api/auth/me", HttpMethod.GET,
        new HttpEntity<>(protectedHeaders), JsonNode.class);
    assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(me.getBody().path("email").asText()).isEqualTo("admin-filter@nexushr.local");
  }
}
