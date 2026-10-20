package com.nexushr.employee;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class EmployeeManagementIntegrationTest {
  private static final String ADMIN_EMAIL = "admin@nexushr.local";
  private static final String ADMIN_PASSWORD = "StrongAdminPassword123!";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  private String adminToken;

  @BeforeEach
  void signInAsAdmin() throws Exception {
    adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
  }

  @Test
  void employeeCreateRejectsMissingFieldsDuplicateIdentifiersAndInvalidDepartment() throws Exception {
    mvc.perform(post("/api/employees").header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());

    String code = unique("EMP");
    String departmentId = createDepartment();
    String request = employeeRequest(code, uniqueEmail(), departmentId);
    mvc.perform(post("/api/employees").header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isCreated());
    mvc.perform(post("/api/employees").header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isConflict());

    mvc.perform(post("/api/employees").header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content(employeeRequest(unique("EMP"), uniqueEmail(), UUID.randomUUID().toString())))
        .andExpect(status().isNotFound());
  }

  @Test
  void directorySupportsSearchPaginationAndDepartmentFilter() throws Exception {
    String departmentId = createDepartment();
    String prefix = unique("PAGE");
    createEmployee(prefix + "A", uniqueEmail(), departmentId);
    createEmployee(prefix + "B", uniqueEmail(), departmentId);
    createEmployee(prefix + "C", uniqueEmail(), departmentId);

    mvc.perform(get("/api/employees").header("Authorization", bearer(adminToken))
            .param("q", prefix).param("departmentId", departmentId).param("page", "0").param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(2));

    mvc.perform(get("/api/employees").header("Authorization", bearer(adminToken)).param("size", "101"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void employeeCanViewOwnProfileButCannotBrowseOrReadAnotherProfile() throws Exception {
    String email = uniqueEmail();
    String departmentId = createDepartment();
    String employeeId = createEmployee(unique("SELF"), email, departmentId);
    String otherId = createEmployee(unique("OTHER"), uniqueEmail(), departmentId);
    createAccount(email, "SelfEmployeePassword123!");
    String employeeToken = login(email, "SelfEmployeePassword123!");

    mvc.perform(get("/api/employees/me").header("Authorization", bearer(employeeToken)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(employeeId));
    mvc.perform(get("/api/employees").header("Authorization", bearer(employeeToken)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/employees/{id}", otherId).header("Authorization", bearer(employeeToken)))
        .andExpect(status().isForbidden());
  }

  @Test
  void managerDirectoryIsRestrictedToDirectReports() throws Exception {
    String managerEmail = uniqueEmail();
    String departmentId = createDepartment();
    mvc.perform(post("/api/auth/accounts").header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + managerEmail + "\",\"password\":\"ManagerPassword123!\",\"roles\":[\"MANAGER\"]}"))
        .andExpect(status().isCreated());
    String managerId = createEmployee(unique("MGR"), managerEmail, departmentId);
    String reportId = createEmployee(unique("REPORT"), uniqueEmail(), departmentId, managerId);
    String otherId = createEmployee(unique("OTHER"), uniqueEmail(), departmentId);
    String managerToken = login(managerEmail, "ManagerPassword123!");

    mvc.perform(get("/api/employees").header("Authorization", bearer(managerToken)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(reportId));
    mvc.perform(get("/api/employees/{id}", reportId).header("Authorization", bearer(managerToken)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/employees/{id}", managerId).header("Authorization", bearer(managerToken)))
        .andExpect(status().isOk());
    mvc.perform(get("/api/employees/{id}", otherId).header("Authorization", bearer(managerToken)))
        .andExpect(status().isForbidden());
  }

  @Test
  void documentMetadataCanBeCreatedListedAndRemovedWithoutUploadingBinary() throws Exception {
    String departmentId = createDepartment();
    String employeeId = createEmployee(unique("DOC"), uniqueEmail(), departmentId);
    String documentJson = "{\"documentType\":\"CONTRACT\",\"fileName\":\"contract.pdf\",\"contentType\":\"application/pdf\",\"sizeBytes\":24576}";
    MvcResult created = mvc.perform(post("/api/employees/{id}/documents", employeeId)
            .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content(documentJson))
        .andExpect(status().isCreated()).andExpect(jsonPath("$.fileName").value("contract.pdf"))
        .andReturn();
    String documentId = json.readTree(created.getResponse().getContentAsString()).get("id").asText();
    mvc.perform(get("/api/employees/{id}/documents", employeeId).header("Authorization", bearer(adminToken)))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].sizeBytes").value(24576));
    mvc.perform(delete("/api/employees/{id}/documents/{documentId}", employeeId, documentId)
            .header("Authorization", bearer(adminToken)))
        .andExpect(status().isNoContent());
  }

  @Test
  void departmentInUseCannotBeDeleted() throws Exception {
    String departmentId = createDepartment();
    createEmployee(unique("DEPT"), uniqueEmail(), departmentId);
    mvc.perform(delete("/api/departments/{id}", departmentId).header("Authorization", bearer(adminToken)))
        .andExpect(status().isConflict());
  }

  private String createDepartment() throws Exception {
    String body = "{\"code\":\"" + unique("DPT") + "\",\"name\":\"" + unique("Department") + "\",\"description\":\"Integration test\"}";
    MvcResult result = mvc.perform(post("/api/departments").header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated()).andReturn();
    return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private String createEmployee(String code, String email, String departmentId) throws Exception {
    return createEmployee(code, email, departmentId, null);
  }

  private String createEmployee(String code, String email, String departmentId, String managerId) throws Exception {
    MvcResult result = mvc.perform(post("/api/employees").header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON).content(employeeRequest(code, email, departmentId, managerId)))
        .andExpect(status().isCreated()).andReturn();
    return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private String employeeRequest(String code, String email, String departmentId) {
    return employeeRequest(code, email, departmentId, null);
  }

  private String employeeRequest(String code, String email, String departmentId, String managerId) {
    return "{\"employeeCode\":\"" + code + "\",\"firstName\":\"Test\",\"lastName\":\"User\",\"workEmail\":\"" + email
        + "\",\"designation\":\"Analyst\",\"departmentId\":\"" + departmentId
        + (managerId == null ? "" : "\",\"managerId\":\"" + managerId)
        + "\",\"status\":\"ACTIVE\",\"onboardingStatus\":\"IN_PROGRESS\",\"hireDate\":\"2026-10-01\"}";
  }

  private void createAccount(String email, String password) throws Exception {
    mvc.perform(post("/api/auth/accounts").header("Authorization", bearer(adminToken))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"roles\":[\"EMPLOYEE\"]}"))
        .andExpect(status().isCreated());
  }

  private String login(String email, String password) throws Exception {
    MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
        .andExpect(status().isOk()).andReturn();
    JsonNode response = json.readTree(result.getResponse().getContentAsString());
    return response.get("accessToken").asText();
  }

  private static String unique(String prefix) { return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }
  private static String uniqueEmail() { return unique("employee") + "@nexushr.local"; }
  private static String bearer(String token) { return "Bearer " + token; }
}
