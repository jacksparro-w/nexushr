package com.nexushr.payroll;

import com.nexushr.employee.Employee;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "payroll_structures")
public class PayrollStructure {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "employee_id", nullable = false, unique = true) private Employee employee;
  @Column(nullable = false, length = 3) private String currency;
  @Column(name = "base_salary", nullable = false, precision = 14, scale = 2) private BigDecimal baseSalary;
  @Column(name = "tax_rate_percent", nullable = false, precision = 5, scale = 2) private BigDecimal taxRatePercent;
  @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom;
  @Column(nullable = false) private boolean active = true;
  @OneToMany(mappedBy = "structure", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  private List<PayrollComponent> components = new ArrayList<>();
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();

  protected PayrollStructure() {}

  PayrollStructure(Employee employee, PayrollDtos.StructureRequest request) {
    this.employee = employee;
    update(request);
  }

  void update(PayrollDtos.StructureRequest request) {
    currency = request.currency().trim().toUpperCase();
    baseSalary = request.baseSalary();
    taxRatePercent = request.taxRatePercent();
    effectiveFrom = request.effectiveFrom();
    active = request.active() == null || request.active();
    components.clear();
    request.components().forEach(component -> components.add(new PayrollComponent(this, component)));
    updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public Employee getEmployee() { return employee; }
  public String getCurrency() { return currency; }
  public BigDecimal getBaseSalary() { return baseSalary; }
  public BigDecimal getTaxRatePercent() { return taxRatePercent; }
  public LocalDate getEffectiveFrom() { return effectiveFrom; }
  public boolean isActive() { return active; }
  public List<PayrollComponent> getComponents() { return components; }
}
