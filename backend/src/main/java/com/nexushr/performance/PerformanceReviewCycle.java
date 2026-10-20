package com.nexushr.performance;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name="performance_review_cycles")
public class PerformanceReviewCycle {
  @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
  @Column(nullable=false,length=160) private String name;
  @Column(name="start_date",nullable=false) private LocalDate startDate;
  @Column(name="end_date",nullable=false) private LocalDate endDate;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ReviewCycleStatus status=ReviewCycleStatus.OPEN;
  @Column(name="created_by",nullable=false,length=320) private String createdBy;
  @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt=Instant.now();
  protected PerformanceReviewCycle() {}
  PerformanceReviewCycle(PerformanceDtos.CycleRequest r,String actor){name=r.name().trim();startDate=r.startDate();endDate=r.endDate();createdBy=actor;}
  public UUID getId(){return id;} public String getName(){return name;} public LocalDate getStartDate(){return startDate;} public LocalDate getEndDate(){return endDate;} public ReviewCycleStatus getStatus(){return status;} public String getCreatedBy(){return createdBy;}
  void close(){status=ReviewCycleStatus.CLOSED;}
}
