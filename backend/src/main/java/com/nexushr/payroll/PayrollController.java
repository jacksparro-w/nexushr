package com.nexushr.payroll;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/payroll")
@SecurityRequirement(name = "bearerAuth")
public class PayrollController {
  private final PayrollService service;
  public PayrollController(PayrollService service) { this.service = service; }

  @PutMapping("/employees/{employeeId}/structure")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public PayrollDtos.StructureResponse setStructure(@PathVariable UUID employeeId,
      @Valid @RequestBody PayrollDtos.StructureRequest request) {
    return service.setStructure(employeeId, request);
  }

  @GetMapping("/employees/{employeeId}/structure")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public PayrollDtos.StructureResponse structure(@PathVariable UUID employeeId) { return service.structure(employeeId); }

  @GetMapping("/structures")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public List<PayrollDtos.StructureResponse> structures() { return service.structures(); }

  @PostMapping("/runs")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  @ResponseStatus(HttpStatus.CREATED)
  public PayrollDtos.RunResponse generate(@RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
      Authentication auth) {
    return service.generate(month, auth.getName());
  }

  @GetMapping("/runs")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public List<PayrollDtos.RunResponse> runs() { return service.runs(); }

  @PostMapping("/runs/{runId}/approve")
  @PreAuthorize("hasRole('ADMIN')")
  public PayrollDtos.RunResponse approve(@PathVariable UUID runId, Authentication auth) {
    return service.approve(runId, auth.getName());
  }

  @PostMapping("/runs/{runId}/mark-paid")
  @PreAuthorize("hasRole('ADMIN')")
  public PayrollDtos.RunResponse markPaid(@PathVariable UUID runId, Authentication auth) {
    return service.markPaid(runId, auth.getName());
  }

  @GetMapping("/runs/{runId}/payslips")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public PayrollDtos.PageResponse<PayrollDtos.PayslipResponse> runPayslips(@PathVariable UUID runId,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    return service.runPayslips(runId, page, size);
  }

  @GetMapping(value = "/runs/{runId}/export", produces = "text/csv")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public ResponseEntity<String> export(@PathVariable UUID runId) {
    return ResponseEntity.ok().contentType(new MediaType("text", "csv"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payroll-" + runId + ".csv")
        .body(service.exportCsv(runId));
  }

  @GetMapping("/me/payslips")
  public List<PayrollDtos.PayslipResponse> ownPayslips(Authentication auth) {
    return service.ownPayslips(auth.getName());
  }

  @GetMapping("/me/payslips/{payslipId}")
  public PayrollDtos.PayslipResponse ownPayslip(@PathVariable UUID payslipId, Authentication auth) {
    return service.ownPayslip(auth.getName(), payslipId);
  }

  @PostMapping("/payslips/{payslipId}/corrections")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public PayrollDtos.CorrectionResponse correct(@PathVariable UUID payslipId,
      @Valid @RequestBody PayrollDtos.CorrectionRequest request, Authentication auth) {
    return service.correct(payslipId, request, auth.getName());
  }

  @GetMapping("/payslips/{payslipId}/corrections")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public List<PayrollDtos.CorrectionResponse> correctionHistory(@PathVariable UUID payslipId) {
    return service.correctionHistory(payslipId);
  }
}
