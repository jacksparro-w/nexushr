package com.nexushr.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payroll_components")
public class PayrollComponent {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "structure_id", nullable = false) private PayrollStructure structure;
  @Column(nullable = false, length = 100) private String name;
  @Enumerated(EnumType.STRING) @Column(name = "component_type", nullable = false, length = 20) private PayrollComponentType type;
  @Column(nullable = false, precision = 14, scale = 2) private BigDecimal amount;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

  protected PayrollComponent() {}

  PayrollComponent(PayrollStructure structure, PayrollDtos.ComponentRequest request) {
    this.structure = structure;
    this.name = request.name().trim();
    this.type = request.type();
    this.amount = request.amount();
  }

  public UUID getId() { return id; }
  public String getName() { return name; }
  public PayrollComponentType getType() { return type; }
  public BigDecimal getAmount() { return amount; }
}
