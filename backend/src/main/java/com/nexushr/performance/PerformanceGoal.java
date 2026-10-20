package com.nexushr.performance;

import com.nexushr.employee.Employee;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name="performance_goals")
public class PerformanceGoal {
  @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
  @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="employee_id",nullable=false) private Employee employee;
  @Column(nullable=false,length=160) private String title;
  @Column(nullable=false,length=2000) private String description;
  @Column(name="key_result",nullable=false,length=500) private String keyResult;
  @Column(name="target_date",nullable=false) private LocalDate targetDate;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private GoalStatus status=GoalStatus.IN_PROGRESS;
  @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt=Instant.now();
  @Column(name="updated_at",nullable=false) private Instant updatedAt=Instant.now();
  protected PerformanceGoal() {}
  PerformanceGoal(Employee employee, PerformanceDtos.GoalRequest r) { this.employee=employee; update(r); }
  void update(PerformanceDtos.GoalRequest r) { title=r.title().trim(); description=r.description().trim(); keyResult=r.keyResult().trim(); targetDate=r.targetDate(); status=r.status()==null?GoalStatus.IN_PROGRESS:r.status(); updatedAt=Instant.now(); }
  public UUID getId(){return id;} public Employee getEmployee(){return employee;} public String getTitle(){return title;} public String getDescription(){return description;} public String getKeyResult(){return keyResult;} public LocalDate getTargetDate(){return targetDate;} public GoalStatus getStatus(){return status;}
}
