package com.nexushr.performance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
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

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:nexushr_performance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","app.security.jwt-secret=01234567890123456789012345678901","app.bootstrap.admin-email=admin@nexushr.local","app.bootstrap.admin-password=StrongAdminPassword123!"})
@AutoConfigureMockMvc @Transactional
class PerformanceIntegrationTest {
  @Autowired MockMvc mvc; @Autowired ObjectMapper json;
  private String adminToken; private String departmentId;
  @BeforeEach void setup() throws Exception {
    adminToken=login("admin@nexushr.local","StrongAdminPassword123!");
    MvcResult d=mvc.perform(post("/api/departments").header("Authorization",bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"P"+UUID.randomUUID().toString().substring(0,8)+"\",\"name\":\"Performance\",\"description\":\"Test\"}")).andExpect(status().isCreated()).andReturn();
    departmentId=json.readTree(d.getResponse().getContentAsString()).get("id").asText();
  }

  @Test void completesReviewCycleWithEmployeeSelfAssessmentManagerFeedbackAndAccessChecks() throws Exception {
    String managerEmail=email("manager"), employeeEmail=email("employee"), otherEmail=email("other");
    createAccount(managerEmail,"MANAGER"); createAccount(employeeEmail,"EMPLOYEE"); createAccount(otherEmail,"EMPLOYEE");
    String managerId=createEmployee("Manager",managerEmail,null);
    String employeeId=createEmployee("Report",employeeEmail,managerId);
    String otherId=createEmployee("Other",otherEmail,null);
    String managerToken=login(managerEmail,"PerformancePassword123!"); String employeeToken=login(employeeEmail,"PerformancePassword123!");

    String goal="{\"title\":\"Improve quality\",\"description\":\"Reduce returned work\",\"keyResult\":\"Cut rework by 20 percent\",\"targetDate\":\""+LocalDate.now().plusDays(20)+"\"}";
    MvcResult goalResult=mvc.perform(post("/api/performance/goals").header("Authorization",bearer(employeeToken)).contentType(MediaType.APPLICATION_JSON).content(goal)).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("IN_PROGRESS")).andReturn();
    String goalId=json.readTree(goalResult.getResponse().getContentAsString()).get("id").asText();
    mvc.perform(get("/api/performance/employees/{id}/goals",otherId).header("Authorization",bearer(employeeToken))).andExpect(status().isForbidden());
    mvc.perform(post("/api/performance/employees/{id}/goals",otherId).header("Authorization",bearer(managerToken)).contentType(MediaType.APPLICATION_JSON).content(goal)).andExpect(status().isForbidden());
    mvc.perform(put("/api/performance/goals/{id}",goalId).header("Authorization",bearer(employeeToken)).contentType(MediaType.APPLICATION_JSON).content(goal.replace("Improve quality","Improve quality" ).replace("2026-","2026-"))).andExpect(status().isOk());

    LocalDate start=LocalDate.now(), end=start.plusDays(14);
    MvcResult cycle=mvc.perform(post("/api/performance/cycles").header("Authorization",bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Midyear\",\"startDate\":\""+start+"\",\"endDate\":\""+end+"\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.reviewCount").value(3)).andReturn();
    String cycleId=json.readTree(cycle.getResponse().getContentAsString()).get("id").asText();
    JsonNode own=json.readTree(mvc.perform(get("/api/performance/reviews/me").header("Authorization",bearer(employeeToken))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    String reviewId=null; for(JsonNode review:own) if(review.get("employeeId").asText().equals(employeeId)&&review.get("cycleId").asText().equals(cycleId)) reviewId=review.get("id").asText();
    org.junit.jupiter.api.Assertions.assertNotNull(reviewId);
    mvc.perform(get("/api/performance/reviews/{id}",reviewId).header("Authorization",bearer(login(otherEmail,"PerformancePassword123!")))).andExpect(status().isForbidden());
    mvc.perform(post("/api/performance/reviews/{id}/manager-review",reviewId).header("Authorization",bearer(managerToken)).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4,\"feedback\":\"Premature\"}")).andExpect(status().isConflict());
    mvc.perform(post("/api/performance/reviews/{id}/self-assessment",reviewId).header("Authorization",bearer(employeeToken)).contentType(MediaType.APPLICATION_JSON).content("{\"assessment\":\"Delivered the key result\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.selfSubmittedAt").exists());
    mvc.perform(post("/api/performance/reviews/{id}/self-assessment",reviewId).header("Authorization",bearer(employeeToken)).contentType(MediaType.APPLICATION_JSON).content("{\"assessment\":\"Duplicate\"}")).andExpect(status().isConflict());
    mvc.perform(post("/api/performance/reviews/{id}/manager-review",reviewId).header("Authorization",bearer(managerToken)).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":6,\"feedback\":\"Good\"}")).andExpect(status().isBadRequest());
    mvc.perform(post("/api/performance/reviews/{id}/manager-review",reviewId).header("Authorization",bearer(managerToken)).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":4,\"feedback\":\"Strong progress\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.completedAt").exists());
    mvc.perform(get("/api/performance/reviews/me").header("Authorization",bearer(employeeToken))).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.id=='"+reviewId+"')].managerRating").value(org.hamcrest.Matchers.contains(4)));
    mvc.perform(post("/api/performance/cycles/{id}/close",cycleId).header("Authorization",bearer(adminToken))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
    mvc.perform(post("/api/performance/reviews/{id}/self-assessment",reviewId).header("Authorization",bearer(employeeToken)).contentType(MediaType.APPLICATION_JSON).content("{\"assessment\":\"Too late\"}")).andExpect(status().isConflict());
  }

  private String createEmployee(String first,String email,String managerId) throws Exception {String id="E"+UUID.randomUUID().toString().substring(0,8);String body="{\"employeeCode\":\""+id+"\",\"firstName\":\""+first+"\",\"lastName\":\"Test\",\"workEmail\":\""+email+"\",\"designation\":\"Analyst\",\"departmentId\":\""+departmentId+"\",\"managerId\":"+(managerId==null?"null":"\""+managerId+"\"")+",\"status\":\"ACTIVE\",\"onboardingStatus\":\"COMPLETE\",\"hireDate\":\"2024-01-01\"}";return json.readTree(mvc.perform(post("/api/employees").header("Authorization",bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();}
  private void createAccount(String email,String role) throws Exception {mvc.perform(post("/api/auth/accounts").header("Authorization",bearer(adminToken)).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\"PerformancePassword123!\",\"roles\":[\""+role+"\"]}")).andExpect(status().isCreated());}
  private String login(String email,String password) throws Exception {return json.readTree(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\",\"password\":\""+password+"\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();}
  private String email(String prefix){return prefix+UUID.randomUUID().toString().substring(0,8)+"@nexushr.local";}
  private static String bearer(String token){return "Bearer "+token;}
}
