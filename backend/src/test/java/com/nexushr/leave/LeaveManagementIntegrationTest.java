package com.nexushr.leave;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  "spring.datasource.url=jdbc:h2:mem:nexushr;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "app.security.jwt-secret=01234567890123456789012345678901",
  "app.bootstrap.admin-email=admin@nexushr.local",
  "app.bootstrap.admin-password=StrongAdminPassword123!",
  "app.attendance.time-zone=Asia/Kolkata",
  "app.attendance.workday-start=00:00",
  "app.attendance.grace-minutes=0"
})
@AutoConfigureMockMvc
class LeaveManagementIntegrationTest {
  private static final String ADMIN_EMAIL = "admin@nexushr.local";
  private static final String ADMIN_PASSWORD = "StrongAdminPassword123!";
  private static final int YEAR = 2030;
  private static final String START = "2030-06-03";
  private static final String END = "2030-06-04";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  private String adminToken;
  private String annualTypeId;

  @BeforeEach
  void signInAsAdmin() throws Exception {
    adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
    MvcResult result = mvc.perform(get("/api/leaves/types").header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk()).andReturn();
    JsonNode types = json.readTree(result.getResponse().getContentAsString());
    for (JsonNode type : types) if (type.get("code").asText().equals("ANNUAL")) annualTypeId = type.get("id").asText();
  }

  @Test
  void employeeRequestsReserveBalanceAndManagerCanApproveDirectReport() throws Exception {
    EmployeeAccount manager = createEmployee("Manager", "MANAGER", null);
    EmployeeAccount employee = createEmployee("LeaveUser", "EMPLOYEE", manager.employeeId());
    setBalance(employee.employeeId(), "5");
    String employeeToken = login(employee.email(), employee.password());
    String managerToken = login(manager.email(), manager.password());

    MvcResult request = mvc.perform(post("/api/leaves/me/requests").header("Authorization", bearer(employeeToken))
            .contentType(MediaType.APPLICATION_JSON).content(applyJson(START, END)))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.days").value(2)).andReturn();
    String requestId = json.readTree(request.getResponse().getContentAsString()).get("id").asText();

    mvc.perform(get("/api/leaves/me/balances").header("Authorization", bearer(employeeToken)).param("year", "2030"))
        .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.leaveTypeCode == 'ANNUAL')].pendingDays").value(org.hamcrest.Matchers.contains(2.0)))
        .andExpect(jsonPath("$[?(@.leaveTypeCode == 'ANNUAL')].availableDays").value(org.hamcrest.Matchers.contains(3.0)));
    mvc.perform(post("/api/leaves/me/requests").header("Authorization", bearer(employeeToken))
            .contentType(MediaType.APPLICATION_JSON).content(applyJson(START, END)))
        .andExpect(status().isConflict());
    mvc.perform(post("/api/leaves/requests/{id}/decision", requestId).header("Authorization", bearer(employeeToken))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/leaves/requests").header("Authorization", bearer(employeeToken)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/leaves/requests").header("Authorization", bearer(managerToken)).param("status", "PENDING"))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(requestId));
    mvc.perform(post("/api/leaves/requests/{id}/decision", requestId).header("Authorization", bearer(managerToken))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\",\"note\":\"Coverage arranged\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
    mvc.perform(get("/api/leaves/me/balances").header("Authorization", bearer(employeeToken)).param("year", "2030"))
        .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.leaveTypeCode == 'ANNUAL')].usedDays").value(org.hamcrest.Matchers.contains(2.0)))
        .andExpect(jsonPath("$[?(@.leaveTypeCode == 'ANNUAL')].pendingDays").value(org.hamcrest.Matchers.contains(0)));
    mvc.perform(get("/api/leaves/me/requests").header("Authorization", bearer(employeeToken)))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].decisionNote").value("Coverage arranged"));
    MvcResult notificationResult = mvc.perform(get("/api/notifications").header("Authorization", bearer(employeeToken)))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].category").value("LEAVE"))
        .andExpect(jsonPath("$[0].title").value("Leave request approved")).andReturn();
    String notificationId = json.readTree(notificationResult.getResponse().getContentAsString()).get(0).get("id").asText();
    mvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(employeeToken)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
            "/api/notifications/{id}/read", notificationId).header("Authorization", bearer(managerToken)))
        .andExpect(status().isNotFound());
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
            "/api/notifications/{id}/read", notificationId).header("Authorization", bearer(employeeToken)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.readAt").exists());
    mvc.perform(post("/api/announcements").header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Office update\",\"body\":\"Friday hours are unchanged.\"}"))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("Office update"));
    mvc.perform(get("/api/announcements").header("Authorization", bearer(employeeToken)))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].body").value("Friday hours are unchanged."));
    mvc.perform(get("/api/notifications").header("Authorization", bearer(employeeToken)))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].category").value("ANNOUNCEMENT"));
  }

  @Test
  void insufficientBalanceAndCancellationReleaseReservation() throws Exception {
    EmployeeAccount employee = createEmployee("CancelUser", "EMPLOYEE", null);
    setBalance(employee.employeeId(), "1");
    String token = login(employee.email(), employee.password());
    mvc.perform(post("/api/leaves/me/requests").header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON).content(applyJson(START, END)))
        .andExpect(status().isConflict());

    MvcResult pending = mvc.perform(post("/api/leaves/me/requests").header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON).content(applyJson("2030-06-03", "2030-06-03")))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.days").value(1)).andReturn();
    String requestId = json.readTree(pending.getResponse().getContentAsString()).get("id").asText();
    mvc.perform(post("/api/leaves/me/requests/{id}/cancel", requestId).header("Authorization", bearer(token)))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/leaves/me/balances").header("Authorization", bearer(token)).param("year", "2030"))
        .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.leaveTypeCode == 'ANNUAL')].pendingDays").value(org.hamcrest.Matchers.contains(0)))
        .andExpect(jsonPath("$[?(@.leaveTypeCode == 'ANNUAL')].availableDays").value(org.hamcrest.Matchers.contains(1.0)));
  }

  @Test
  void rejectsCrossYearAndWeekendOnlyRangesAndAllowsHrToManageLeaveTypes() throws Exception {
    EmployeeAccount employee = createEmployee("InvalidUser", "EMPLOYEE", null);
    String token = login(employee.email(), employee.password());
    mvc.perform(post("/api/leaves/me/requests").header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON).content(applyJson("2030-12-31", "2031-01-01")))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/leaves/me/requests").header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON).content(applyJson("2030-06-08", "2030-06-09")))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/leaves/types").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"FAMILY\",\"name\":\"Family leave\",\"paid\":true}"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/leaves/types").header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"code\":\"FAMILY\",\"name\":\"Family leave\",\"description\":\"Family needs\",\"paid\":true}"))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("FAMILY"));
  }

  private void setBalance(String employeeId, String days) throws Exception {
    mvc.perform(put("/api/leaves/employees/{employeeId}/balances/{typeId}", employeeId, annualTypeId)
            .header("Authorization", bearer(adminToken)).param("year", "2030")
            .contentType(MediaType.APPLICATION_JSON).content("{\"entitledDays\":" + days + "}"))
        .andExpect(status().isOk());
  }

  private EmployeeAccount createEmployee(String name, String role, String managerId) throws Exception {
    String email = unique(name.toLowerCase()) + "@nexushr.local";
    String password = "EmployeeLeavePassword123!";
    String departmentJson = "{\"code\":\"" + unique("LV") + "\",\"name\":\"" + unique("Leave") + "\",\"description\":\"test\"}";
    MvcResult department = mvc.perform(post("/api/departments").header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON).content(departmentJson))
        .andExpect(status().isCreated()).andReturn();
    String departmentId = json.readTree(department.getResponse().getContentAsString()).get("id").asText();
    String employeeJson = "{\"employeeCode\":\"" + unique("LE") + "\",\"firstName\":\"" + name
        + "\",\"lastName\":\"Test\",\"workEmail\":\"" + email + "\",\"designation\":\"Analyst\",\"departmentId\":\""
        + departmentId + "\",\"managerId\":" + (managerId == null ? "null" : "\"" + managerId + "\"")
        + ",\"status\":\"ACTIVE\",\"onboardingStatus\":\"COMPLETE\",\"hireDate\":\"2026-01-01\"}";
    MvcResult employee = mvc.perform(post("/api/employees").header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON).content(employeeJson))
        .andExpect(status().isCreated()).andReturn();
    String employeeId = json.readTree(employee.getResponse().getContentAsString()).get("id").asText();
    mvc.perform(post("/api/auth/accounts").header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"roles\":[\"" + role + "\"]}"))
        .andExpect(status().isCreated());
    return new EmployeeAccount(employeeId, email, password);
  }

  private String login(String email, String password) throws Exception {
    MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isOk()).andReturn();
    return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
  }

  private String applyJson(String from, String to) {
    return "{\"leaveTypeId\":\"" + annualTypeId + "\",\"startDate\":\"" + from + "\",\"endDate\":\"" + to + "\",\"reason\":\"Personal plans\"}";
  }
  private static String unique(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }
  private static String bearer(String token) { return "Bearer " + token; }
  private record EmployeeAccount(String employeeId, String email, String password) {}
}
