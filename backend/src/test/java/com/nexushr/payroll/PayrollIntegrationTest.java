package com.nexushr.payroll;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.transaction.annotation.Transactional;

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
@Transactional
class PayrollIntegrationTest {
  private static final String ADMIN_EMAIL = "admin@nexushr.local";
  private static final String ADMIN_PASSWORD = "StrongAdminPassword123!";
  private static final String EMPLOYEE_PASSWORD = "EmployeePayrollPassword123!";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  private String adminToken;

  @BeforeEach
  void signIn() throws Exception { adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD); }

  @Test
  void generatesAuditableMonthlyPayslipsAndEnforcesApprovalAndPaymentStates() throws Exception {
    Employee employee = createEmployee("PayrollUser", "EMPLOYEE");
    String employeeToken = login(employee.email(), EMPLOYEE_PASSWORD);
    YearMonth month = YearMonth.now().minusMonths(1);

    mvc.perform(put("/api/payroll/employees/{id}/structure", employee.id()).header("Authorization", bearer(employeeToken))
            .contentType(MediaType.APPLICATION_JSON).content(structureJson("5000.00", "10.00", "1000.00", "500.00")))
        .andExpect(status().isForbidden());
    mvc.perform(put("/api/payroll/employees/{id}/structure", employee.id()).header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON).content(structureJson("5000.00", "10.00", "1000.00", "500.00")))
        .andExpect(status().isOk()).andExpect(jsonPath("$.baseSalary").value(5000.0))
        .andExpect(jsonPath("$.components.length()").value(2))
        .andExpect(jsonPath("$.components[0].amount").value(1000.0))
        .andExpect(jsonPath("$.components[1].amount").value(500.0));

    MvcResult generated = mvc.perform(post("/api/payroll/runs").header("Authorization", bearer(adminToken)).param("month", month.toString()))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.payslipCount").value(1)).andReturn();
    String runId = json.readTree(generated.getResponse().getContentAsString()).get("id").asText();
    mvc.perform(post("/api/payroll/runs").header("Authorization", bearer(adminToken)).param("month", month.toString()))
        .andExpect(status().isConflict());
    mvc.perform(post("/api/payroll/runs").header("Authorization", bearer(employeeToken)).param("month", month.toString()))
        .andExpect(status().isForbidden());

    MvcResult slips = mvc.perform(get("/api/payroll/runs/{id}/payslips", runId).header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].grossPay").value(6000.0))
        .andExpect(jsonPath("$.content[0].taxAmount").value(600.0)).andExpect(jsonPath("$.content[0].netPay").value(4900.0))
        .andExpect(jsonPath("$.content[0].currency").value("USD")).andReturn();
    String payslipId = json.readTree(slips.getResponse().getContentAsString()).get("content").get(0).get("id").asText();
    mvc.perform(get("/api/payroll/runs/{id}/payslips", runId).header("Authorization", bearer(employeeToken)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/payroll/me/payslips").header("Authorization", bearer(employeeToken)))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(payslipId));
    mvc.perform(get("/api/payroll/me/payslips/{id}", payslipId).header("Authorization", bearer(employeeToken)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.netPay").value(4900.0));
    mvc.perform(get("/api/payroll/runs/{id}/export", runId).header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk()).andExpect(content().contentType("text/csv"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("4900.00")));

    String correction = "{\"adjustments\":[{\"name\":\"Missed on-call\",\"type\":\"ALLOWANCE\",\"amount\":100.00}],\"reason\":\"Approved time record correction\"}";
    mvc.perform(post("/api/payroll/payslips/{id}/corrections", payslipId).header("Authorization", bearer(employeeToken))
            .contentType(MediaType.APPLICATION_JSON).content(correction)).andExpect(status().isForbidden());
    mvc.perform(post("/api/payroll/payslips/{id}/corrections", payslipId).header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON).content(correction))
        .andExpect(status().isOk()).andExpect(jsonPath("$.previousNetPay").value(4900.0))
        .andExpect(jsonPath("$.correctedNetPay").value(4990.0)).andExpect(jsonPath("$.reason").value("Approved time record correction"));
    mvc.perform(get("/api/payroll/payslips/{id}/corrections", payslipId).header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].correctedBy").value(ADMIN_EMAIL));

    mvc.perform(post("/api/payroll/runs/{id}/approve", runId).header("Authorization", bearer(employeeToken)))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/payroll/runs/{id}/approve", runId).header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
    mvc.perform(post("/api/payroll/payslips/{id}/corrections", payslipId).header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON).content(correction)).andExpect(status().isConflict());
    mvc.perform(post("/api/payroll/runs/{id}/mark-paid", runId).header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAID"));
    mvc.perform(post("/api/payroll/runs/{id}/mark-paid", runId).header("Authorization", bearer(adminToken)))
        .andExpect(status().isConflict());
  }

  @Test
  void rejectsNegativeAmountsDeductionsThatExceedGrossAndFuturePayrollRuns() throws Exception {
    Employee employee = createEmployee("InvalidPayroll", "EMPLOYEE");
    String badStructure = "{\"currency\":\"USD\",\"baseSalary\":-10,\"taxRatePercent\":0,\"effectiveFrom\":\"2020-01-01\",\"components\":[]}";
    mvc.perform(put("/api/payroll/employees/{id}/structure", employee.id()).header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON).content(badStructure)).andExpect(status().isBadRequest());
    mvc.perform(put("/api/payroll/employees/{id}/structure", employee.id()).header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON).content(structureJson("100.00", "0.00", "0.00", "101.00")))
        .andExpect(status().isOk());
    mvc.perform(post("/api/payroll/runs").header("Authorization", bearer(adminToken)).param("month", YearMonth.now().minusMonths(1).toString()))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/payroll/runs").header("Authorization", bearer(adminToken)).param("month", YearMonth.now().plusMonths(1).toString()))
        .andExpect(status().isBadRequest());
  }

  private Employee createEmployee(String name, String role) throws Exception {
    String email = unique(name.toLowerCase()) + "@nexushr.local";
    String departmentJson = "{\"code\":\"" + unique("PR") + "\",\"name\":\"" + unique("Payroll") + "\",\"description\":\"test\"}";
    MvcResult department = mvc.perform(post("/api/departments").header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON).content(departmentJson)).andExpect(status().isCreated()).andReturn();
    String departmentId = json.readTree(department.getResponse().getContentAsString()).get("id").asText();
    String profile = "{\"employeeCode\":\"" + unique("PE") + "\",\"firstName\":\"" + name
        + "\",\"lastName\":\"Test\",\"workEmail\":\"" + email + "\",\"designation\":\"Analyst\",\"departmentId\":\""
        + departmentId + "\",\"status\":\"ACTIVE\",\"onboardingStatus\":\"COMPLETE\",\"hireDate\":\"2024-01-01\"}";
    MvcResult result = mvc.perform(post("/api/employees").header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON).content(profile)).andExpect(status().isCreated()).andReturn();
    String employeeId = json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    mvc.perform(post("/api/auth/accounts").header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + EMPLOYEE_PASSWORD + "\",\"roles\":[\"" + role + "\"]}"))
        .andExpect(status().isCreated());
    return new Employee(employeeId, email);
  }

  private String login(String email, String password) throws Exception {
    MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isOk()).andReturn();
    return json.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
  }

  private static String structureJson(String base, String rate, String allowance, String deduction) {
    return "{\"currency\":\"USD\",\"baseSalary\":" + base + ",\"taxRatePercent\":" + rate
        + ",\"effectiveFrom\":\"2020-01-01\",\"active\":true,\"components\":["
        + "{\"name\":\"Housing\",\"type\":\"ALLOWANCE\",\"amount\":" + allowance + "},"
        + "{\"name\":\"Insurance\",\"type\":\"DEDUCTION\",\"amount\":" + deduction + "}]}";
  }

  private static String unique(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }
  private static String bearer(String token) { return "Bearer " + token; }
  private record Employee(String id, String email) {}
}
