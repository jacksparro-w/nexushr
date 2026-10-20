package com.nexushr.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:nexushr;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "app.security.jwt-secret=01234567890123456789012345678901",
      "app.bootstrap.admin-email=admin@nexushr.local",
      "app.bootstrap.admin-password=StrongAdminPassword123!"
    })
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {
  private static final String ADMIN_EMAIL = "admin@nexushr.local";
  private static final String ADMIN_PASSWORD = "StrongAdminPassword123!";
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;

  @Test
  void swaggerUiAndOpenApiDocumentExposeAuthEndpointsWithoutAuthentication() throws Exception {
    MvcResult result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    JsonNode document = json.readTree(result.getResponse().getContentAsString());

    assertEquals("NexusHR API", document.path("info").path("title").asText());
    assertTrue(document.path("paths").has("/api/auth/login"));
    assertTrue(document.path("paths").has("/api/auth/accounts"));
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }

  @Test
  void frontendOriginCanCompleteCorsPreflightForLogin() throws Exception {
    mvc.perform(
            options("/api/auth/login")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
  }

  @Test
  @Timeout(30)
  void loginSetsHttpOnlyRefreshCookieButDoesNotExposeItInJson() throws Exception {
    var executor =
        Executors.newSingleThreadExecutor(
            task -> {
              Thread thread = new Thread(task, "nexushr-login-test");
              thread.setDaemon(true);
              return thread;
            });
    MvcResult result;
    try {
      result =
          executor
              .submit(
                  () -> login(ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isOk()).andReturn())
              .get(15, TimeUnit.SECONDS);
    } catch (TimeoutException timeout) {
      String stack =
          Thread.getAllStackTraces().keySet().stream()
              .filter(thread -> thread.getName().equals("nexushr-login-test"))
              .map(thread -> java.util.Arrays.toString(thread.getStackTrace()))
              .findFirst()
              .orElse("login worker thread not available");
      fail("Login request exceeded 15 seconds. Worker stack: " + stack, timeout);
      return;
    } finally {
      executor.shutdownNow();
    }
    JsonNode body = json.readTree(result.getResponse().getContentAsString());

    assertTrue(body.hasNonNull("accessToken"));
    assertFalse(body.has("refreshToken"));
    Cookie cookie = refreshCookie(result);
    assertNotNull(cookie);
    String header = result.getResponse().getHeader("Set-Cookie").toLowerCase();
    assertTrue(header.contains("httponly"));
    assertTrue(header.contains("samesite=strict"));
  }

  @Test
  void badPasswordIsRejectedAndProtectedEndpointRequiresAuthentication() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"wrong-password\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void employeeCannotCreateAccountsEvenWithAValidAccessToken() throws Exception {
    String adminToken = accessToken(login(ADMIN_EMAIL, ADMIN_PASSWORD).andReturn());
    mvc.perform(
            post("/api/auth/accounts")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"employee1@nexushr.local\",\"password\":\"EmployeePassword123!\",\"roles\":[\"EMPLOYEE\"]}"))
        .andExpect(status().isCreated());

    String employeeToken =
        accessToken(login("employee1@nexushr.local", "EmployeePassword123!").andReturn());
    mvc.perform(
            post("/api/auth/accounts")
                .header("Authorization", "Bearer " + employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"employee2@nexushr.local\",\"password\":\"EmployeePassword456!\",\"roles\":[\"EMPLOYEE\"]}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanCreateManagerAndHrAccountsWhileHrCannotCreateHrAccounts() throws Exception {
    String adminToken = accessToken(login(ADMIN_EMAIL, ADMIN_PASSWORD).andReturn());
    mvc.perform(post("/api/auth/accounts").header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"manager-role@nexushr.local\",\"password\":\"ManagerPassword123!\",\"roles\":[\"MANAGER\"]}"))
        .andExpect(status().isCreated());
    mvc.perform(post("/api/auth/accounts").header("Authorization", "Bearer " + adminToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"hr-role@nexushr.local\",\"password\":\"HrAccountPassword123!\",\"roles\":[\"HR\"]}"))
        .andExpect(status().isCreated());

    String hrToken = accessToken(login("hr-role@nexushr.local", "HrAccountPassword123!").andReturn());
    mvc.perform(post("/api/auth/accounts").header("Authorization", "Bearer " + hrToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"another-hr@nexushr.local\",\"password\":\"AnotherHrPassword123!\",\"roles\":[\"HR\"]}"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/auth/accounts").header("Authorization", "Bearer " + hrToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"manager-from-hr@nexushr.local\",\"password\":\"ManagerPassword456!\",\"roles\":[\"MANAGER\"]}"))
        .andExpect(status().isCreated());
  }

  @Test
  void refreshRotatesCredentialAndReuseRevokesOutstandingSessions() throws Exception {
    Cookie firstSession = refreshCookie(login(ADMIN_EMAIL, ADMIN_PASSWORD).andReturn());
    Cookie secondSession = refreshCookie(login(ADMIN_EMAIL, ADMIN_PASSWORD).andReturn());
    assertNotNull(firstSession);
    assertNotNull(secondSession);

    MvcResult rotation =
        mvc.perform(post("/api/auth/refresh").cookie(firstSession))
            .andExpect(status().isOk())
            .andReturn();
    Cookie rotated = refreshCookie(rotation);
    assertNotNull(rotated);
    assertNotEquals(firstSession.getValue(), rotated.getValue());

    mvc.perform(post("/api/auth/refresh").cookie(firstSession))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/auth/refresh").cookie(secondSession))
        .andExpect(status().isUnauthorized());
  }

  private org.springframework.test.web.servlet.ResultActions login(String email, String password)
      throws Exception {
    return mvc.perform(
        post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(new AuthDtos.LoginRequest(email, password))));
  }

  private String accessToken(MvcResult result) throws Exception {
    return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
  }

  private Cookie refreshCookie(MvcResult result) {
    String header = result.getResponse().getHeader("Set-Cookie");
    if (header == null) return null;
    String nameValue = header.split(";", 2)[0];
    int separator = nameValue.indexOf('=');
    return separator < 0
        ? null
        : new Cookie(nameValue.substring(0, separator), nameValue.substring(separator + 1));
  }
}
