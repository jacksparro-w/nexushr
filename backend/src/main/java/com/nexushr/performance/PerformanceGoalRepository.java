package com.nexushr.performance;
import java.util.List; import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
public interface PerformanceGoalRepository extends JpaRepository<PerformanceGoal,UUID>{List<PerformanceGoal> findByEmployeeIdOrderByTargetDateAsc(UUID employeeId);boolean existsByEmployeeId(UUID employeeId);}
