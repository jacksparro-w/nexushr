package com.nexushr.payroll;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
  "spring.datasource.url=jdbc:h2:mem:nexushr_payroll_notifications;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "app.security.jwt-secret=01234567890123456789012345678901",
  "app.bootstrap.admin-email=admin-notifications@nexushr.local",
  "app.bootstrap.admin-password=StrongAdminPassword123!"
})
@AutoConfigureMockMvc
class PayrollNotificationIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  private String adminToken;

  @BeforeEach
  void signIn() throws Exception {
    adminToken = login("admin-notifications@nexushr.local", "StrongAdminPassword123!");
  }

  @Test
  void notifiesEmployeeAfterPayrollApprovalAndPaymentCommit() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    String email = "payroll-" + suffix + "@nexushr.local";
    String password = "EmployeePayrollPassword123!";
    String departmentId = createDepartment(suffix);
    String employeeId = createEmployee(suffix, email, departmentId);
    mvc.perform(post("/api/auth/accounts").header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"roles\":[\"EMPLOYEE\"]}"))
        .andExpect(status().isCreated());
    mvc.perform(put("/api/payroll/employees/{id}/structure", employeeId)
            .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"currency\":\"USD\",\"baseSalary\":1000,\"taxRatePercent\":0,\"effectiveFrom\":\"2020-01-01\",\"active\":true,\"components\":[]}"))
        .andExpect(status().isOk());

    String month = YearMonth.now().minusMonths(1).toString();
    MvcResult run = mvc.perform(post("/api/payroll/runs").header("Authorization", bearer(adminToken)).param("month", month))
        .andExpect(status().isCreated()).andReturn();
    String runId = json.readTree(run.getResponse().getContentAsString()).get("id").asText();
    mvc.perform(post("/api/payroll/runs/{id}/approve", runId).header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk());
    String employeeToken = login(email, password);
    mvc.perform(get("/api/notifications").header("Authorization", bearer(employeeToken)))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].category").value("PAYROLL"))
        .andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.containsString("approved")));

    mvc.perform(post("/api/payroll/runs/{id}/mark-paid", runId).header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/notifications").header("Authorization", bearer(employeeToken)))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].message").value(org.hamcrest.Matchers.containsString("paid")))
        .andExpect(jsonPath("$[1].message").value(org.hamcrest.Matchers.containsString("approved")));
  }

  private String createDepartment(String suffix) throws Exception {
    MvcResult result = mvc.perform(post("/api/departments").header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"PN" + suffix + "\",\"name\":\"Payroll Notifications\",\"description\":\"test\"}"))
        .andExpect(status().isCreated()).andReturn();
    return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private String createEmployee(String suffix, String email, String departmentId) throws Exception {
    String body = "{\"employeeCode\":\"PN" + suffix + "\",\"firstName\":\"Payroll\",\"lastName\":\"Recipient\",\"workEmail\":\""
        + email + "\",\"designation\":\"Analyst\",\"departmentId\":\"" + departmentId
        + "\",\"status\":\"ACTIVE\",\"onboardingStatus\":\"COMPLETE\",\"hireDate\":\"2024-01-01\"}";
    MvcResult result = mvc.perform(post("/api/employees").header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated()).andReturn();
    return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private String login(String email, String password) throws Exception {
    MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isOk()).andReturn();
    JsonNode response = json.readTree(result.getResponse().getContentAsString());
    return response.get("accessToken").asText();
  }

  private static String bearer(String token) { return "Bearer " + token; }
}
