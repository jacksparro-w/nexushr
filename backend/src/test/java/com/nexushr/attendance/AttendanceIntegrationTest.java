package com.nexushr.attendance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
      "app.bootstrap.admin-password=StrongAdminPassword123!",
      "app.attendance.time-zone=Asia/Kolkata",
      "app.attendance.workday-start=00:00",
      "app.attendance.grace-minutes=0"
    })
@AutoConfigureMockMvc
class AttendanceIntegrationTest {
  private static final String ADMIN_EMAIL = "admin@nexushr.local";
  private static final String ADMIN_PASSWORD = "StrongAdminPassword123!";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  private String adminToken;

  @BeforeEach
  void signInAsAdmin() throws Exception { adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD); }

  @Test
  void employeeCanClockInOutAndSeeMonthlySummaryButCannotDuplicateRecords() throws Exception {
    String email = unique("attendee") + "@nexushr.local";
    String employeeId = createEmployee(email);
    createAccount(email);
    String employeeToken = login(email, "EmployeeAttendancePassword123!");

    mvc.perform(post("/api/attendance/clock-out").header("Authorization", bearer(employeeToken)))
        .andExpect(status().isConflict());

    MvcResult clockedIn = mvc.perform(post("/api/attendance/clock-in").header("Authorization", bearer(employeeToken)))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.employeeId").value(employeeId))
        .andExpect(jsonPath("$.status").value("IN_PROGRESS")).andReturn();
    mvc.perform(get("/api/attendance/me/current").header("Authorization", bearer(employeeToken)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    mvc.perform(post("/api/attendance/clock-in").header("Authorization", bearer(employeeToken)))
        .andExpect(status().isConflict());

    String recordId = json.readTree(clockedIn.getResponse().getContentAsString()).get("id").asText();
    mvc.perform(get("/api/attendance/me").header("Authorization", bearer(employeeToken))
            .param("from", today().withDayOfMonth(1).toString()).param("to", today().toString()))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(recordId));
    mvc.perform(get("/api/attendance").header("Authorization", bearer(adminToken))
            .param("from", today().withDayOfMonth(1).toString()).param("to", today().toString())
            .param("employeeId", employeeId).param("size", "1"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.size").value(1));

    mvc.perform(post("/api/attendance/clock-out").header("Authorization", bearer(employeeToken)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETE"))
        .andExpect(jsonPath("$.workedMinutes").isNumber());
    mvc.perform(get("/api/attendance/me/current").header("Authorization", bearer(employeeToken)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETE"));
    mvc.perform(post("/api/attendance/clock-out").header("Authorization", bearer(employeeToken)))
        .andExpect(status().isConflict());
    mvc.perform(post("/api/attendance/clock-in").header("Authorization", bearer(employeeToken)))
        .andExpect(status().isConflict());

    mvc.perform(get("/api/attendance/me/summary").header("Authorization", bearer(employeeToken))
            .param("month", YearMonth.now(ZoneId.of("Asia/Kolkata")).toString()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.daysWorked").value(1))
        .andExpect(jsonPath("$.completedDays").value(1));
    mvc.perform(get("/api/attendance/summary").header("Authorization", bearer(adminToken))
            .param("month", YearMonth.now(ZoneId.of("Asia/Kolkata")).toString()))
        .andExpect(status().isOk()).andExpect(jsonPath("$.daysWorked").isNumber())
        .andExpect(jsonPath("$.totalWorkedMinutes").isNumber());
    mvc.perform(get("/api/attendance/summary").header("Authorization", bearer(employeeToken))
            .param("month", YearMonth.now(ZoneId.of("Asia/Kolkata")).toString()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/attendance").header("Authorization", bearer(employeeToken))
            .param("from", today().toString()).param("to", today().toString()))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCorrectionsRequireReasonAndWriteAnAuditableBeforeAfterRecord() throws Exception {
    String email = unique("audited") + "@nexushr.local";
    createEmployee(email);
    createAccount(email);
    String employeeToken = login(email, "EmployeeAttendancePassword123!");
    mvc.perform(get("/api/attendance/me/current").header("Authorization", bearer(employeeToken)))
        .andExpect(status().isNoContent());
    JsonNode created = json.readTree(mvc.perform(post("/api/attendance/clock-in")
            .header("Authorization", bearer(employeeToken)))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    String recordId = created.get("id").asText();
    Instant oldIn = Instant.parse(created.get("clockInAt").asText());
    Instant startOfWorkDate = oldIn.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate().atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant();
    Instant correctedIn = oldIn.minus(java.time.Duration.between(startOfWorkDate, oldIn).dividedBy(2));
    String correction = "{\"clockInAt\":\"" + correctedIn + "\",\"clockOutAt\":null,\"reason\":\"Supervisor verified badge log\"}";

    mvc.perform(patch("/api/attendance/{id}/correction", recordId)
            .header("Authorization", bearer(employeeToken)).contentType(MediaType.APPLICATION_JSON).content(correction))
        .andExpect(status().isForbidden());
    mvc.perform(patch("/api/attendance/{id}/correction", recordId)
            .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"clockInAt\":\"" + correctedIn + "\",\"reason\":\"  \"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(patch("/api/attendance/{id}/correction", recordId)
            .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content(correction))
        .andExpect(status().isOk()).andExpect(jsonPath("$.clockInAt").value(correctedIn.toString()));
    MvcResult auditResult = mvc.perform(get("/api/attendance/{id}/audit", recordId).header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].reason").value("Supervisor verified badge log"))
        .andExpect(jsonPath("$[0].correctedBy").value(ADMIN_EMAIL)).andReturn();
    JsonNode audit = json.readTree(auditResult.getResponse().getContentAsString()).get(0);
    Instant auditedOldIn = Instant.parse(audit.get("previousClockInAt").asText());
    assertTrue(Math.abs(java.time.Duration.between(oldIn, auditedOldIn).toNanos()) <= 1_000,
        "Database timestamp precision may round to the nearest microsecond");
    assertTrue(Math.abs(java.time.Duration.between(correctedIn, Instant.parse(audit.get("correctedClockInAt").asText())).toNanos()) <= 1_000);
  }

  @Test
  void rejectsInvalidDateRangesAndClockOutBeforeClockIn() throws Exception {
    mvc.perform(get("/api/attendance").header("Authorization", bearer(adminToken))
            .param("from", "2026-01-01").param("to", "2026-04-10"))
        .andExpect(status().isBadRequest());

    String email = unique("range") + "@nexushr.local";
    createEmployee(email);
    createAccount(email);
    String employeeToken = login(email, "EmployeeAttendancePassword123!");
    JsonNode record = json.readTree(mvc.perform(post("/api/attendance/clock-in")
            .header("Authorization", bearer(employeeToken))).andReturn().getResponse().getContentAsString());
    Instant in = Instant.parse(record.get("clockInAt").asText());
    String invalid = "{\"clockInAt\":\"" + in + "\",\"clockOutAt\":\"" + in + "\",\"reason\":\"invalid range\"}";
    mvc.perform(patch("/api/attendance/{id}/correction", record.get("id").asText())
            .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content(invalid))
        .andExpect(status().isBadRequest());
  }

  @Test
  void managerAttendanceSummaryOnlyCountsDirectReports() throws Exception {
    String managerEmail = unique("manager") + "@nexushr.local";
    String managerId = createEmployee(managerEmail);
    createAccount(managerEmail, "MANAGER");
    String managerToken = login(managerEmail, "EmployeeAttendancePassword123!");

    String reportEmail = unique("report") + "@nexushr.local";
    String reportId = createEmployee(reportEmail, managerId);
    createAccount(reportEmail);
    String reportToken = login(reportEmail, "EmployeeAttendancePassword123!");
    mvc.perform(post("/api/attendance/clock-in").header("Authorization", bearer(reportToken)))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.employeeId").value(reportId));

    String unrelatedEmail = unique("unrelated") + "@nexushr.local";
    createEmployee(unrelatedEmail);
    createAccount(unrelatedEmail);
    String unrelatedToken = login(unrelatedEmail, "EmployeeAttendancePassword123!");
    mvc.perform(post("/api/attendance/clock-in").header("Authorization", bearer(unrelatedToken)))
        .andExpect(status().isCreated());

    String month = YearMonth.now(ZoneId.of("Asia/Kolkata")).toString();
    mvc.perform(get("/api/attendance/team/summary").header("Authorization", bearer(managerToken)).param("month", month))
        .andExpect(status().isOk()).andExpect(jsonPath("$.daysWorked").value(1));
    mvc.perform(get("/api/attendance/summary").header("Authorization", bearer(adminToken)).param("month", month))
        .andExpect(status().isOk()).andExpect(jsonPath("$.daysWorked").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    mvc.perform(get("/api/attendance/team/summary").header("Authorization", bearer(reportToken)).param("month", month))
        .andExpect(status().isForbidden());
  }

  private String createEmployee(String email) throws Exception {
    return createEmployee(email, null);
  }

  private String createEmployee(String email, String managerId) throws Exception {
    String departmentJson = "{\"code\":\"" + unique("ATT") + "\",\"name\":\"" + unique("Attendance") + "\",\"description\":\"test\"}";
    MvcResult department = mvc.perform(post("/api/departments").header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON).content(departmentJson))
        .andExpect(status().isCreated()).andReturn();
    String departmentId = json.readTree(department.getResponse().getContentAsString()).get("id").asText();
    String employeeJson = "{\"employeeCode\":\"" + unique("EMP") + "\",\"firstName\":\"Test\",\"lastName\":\"Attendance\",\"workEmail\":\"" + email
        + "\",\"designation\":\"Analyst\",\"departmentId\":\"" + departmentId + "\",\"managerId\":" + (managerId == null ? "null" : "\"" + managerId + "\"") + ",\"status\":\"ACTIVE\",\"onboardingStatus\":\"COMPLETE\",\"hireDate\":\"2026-01-01\"}";
    MvcResult employee = mvc.perform(post("/api/employees").header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON).content(employeeJson))
        .andExpect(status().isCreated()).andReturn();
    return json.readTree(employee.getResponse().getContentAsString()).get("id").asText();
  }

  private void createAccount(String email) throws Exception {
    createAccount(email, "EMPLOYEE");
  }

  private void createAccount(String email, String role) throws Exception {
    mvc.perform(post("/api/auth/accounts").header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"EmployeeAttendancePassword123!\",\"roles\":[\"" + role + "\"]}"))
        .andExpect(status().isCreated());
  }

  private String login(String email, String password) throws Exception {
    MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isOk()).andReturn();
    return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
  }

  private static LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Kolkata")); }
  private static String unique(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }
  private static String bearer(String token) { return "Bearer " + token; }
}
