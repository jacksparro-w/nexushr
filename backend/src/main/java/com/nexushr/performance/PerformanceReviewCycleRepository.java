package com.nexushr.performance;
import java.util.List; import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository;
public interface PerformanceReviewCycleRepository extends JpaRepository<PerformanceReviewCycle,UUID>{List<PerformanceReviewCycle> findAllByOrderByStartDateDesc();}
