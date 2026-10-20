package com.nexushr.performance;

import com.nexushr.employee.Employee;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="performance_reviews", uniqueConstraints=@UniqueConstraint(name="uq_performance_review_cycle_employee",columnNames={"cycle_id","employee_id"}))
public class PerformanceReview {
  @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
  @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="cycle_id",nullable=false) private PerformanceReviewCycle cycle;
  @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="employee_id",nullable=false) private Employee employee;
  @Column(name="self_assessment",length=3000) private String selfAssessment;
  @Column(name="self_submitted_at") private Instant selfSubmittedAt;
  @Column(name="manager_rating") private Integer managerRating;
  @Column(name="manager_feedback",length=3000) private String managerFeedback;
  @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="manager_id") private Employee manager;
  @Column(name="completed_at") private Instant completedAt;
  @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt=Instant.now();
  protected PerformanceReview() {}
  PerformanceReview(PerformanceReviewCycle cycle,Employee employee){this.cycle=cycle;this.employee=employee;}
  public UUID getId(){return id;} public PerformanceReviewCycle getCycle(){return cycle;} public Employee getEmployee(){return employee;} public String getSelfAssessment(){return selfAssessment;} public Instant getSelfSubmittedAt(){return selfSubmittedAt;} public Integer getManagerRating(){return managerRating;} public String getManagerFeedback(){return managerFeedback;} public Employee getManager(){return manager;} public Instant getCompletedAt(){return completedAt;}
  void submitSelf(String text){selfAssessment=text.trim();selfSubmittedAt=Instant.now();}
  void complete(Employee manager,int rating,String feedback){this.manager=manager;managerRating=rating;managerFeedback=feedback.trim();completedAt=Instant.now();}
}
