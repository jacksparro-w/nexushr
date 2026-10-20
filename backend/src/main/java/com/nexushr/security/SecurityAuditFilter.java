package com.nexushr.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SecurityAuditFilter extends OncePerRequestFilter {
  private static final Logger log = LoggerFactory.getLogger(SecurityAuditFilter.class);
  private final SecurityAuditService audit;
  public SecurityAuditFilter(SecurityAuditService audit) { this.audit = audit; }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      chain.doFilter(request, response);
    } finally {
      String path = request.getRequestURI();
      String method = request.getMethod();
      if (isSensitive(path) && isWrite(method)) {
        try {
          Object actor = request.getAttribute("authenticatedActorId");
          audit.record(actor == null ? null : actor.toString(), method, path, response.getStatus());
        } catch (RuntimeException failure) {
          log.warn("Could not persist security audit event for {} {}", method, path, failure);
        }
      }
    }
  }

  private boolean isSensitive(String path) {
    return path.startsWith("/api/employees") || path.startsWith("/api/payroll")
        || path.startsWith("/api/auth/accounts") || path.startsWith("/api/intelligence");
  }
  private boolean isWrite(String method) {
    return method.equals("POST") || method.equals("PUT") || method.equals("PATCH") || method.equals("DELETE");
  }
}
