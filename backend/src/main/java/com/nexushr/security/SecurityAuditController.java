package com.nexushr.security;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/security/audit")
@PreAuthorize("hasRole('ADMIN')")
public class SecurityAuditController {
  private final SecurityAuditService service;
  public SecurityAuditController(SecurityAuditService service) { this.service = service; }
  @GetMapping
  public List<SecurityAuditService.AuditEventResponse> latest() { return service.latest(); }
}
