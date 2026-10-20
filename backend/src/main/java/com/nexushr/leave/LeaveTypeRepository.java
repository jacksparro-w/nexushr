package com.nexushr.leave;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, UUID> {
  List<LeaveType> findAllByOrderByNameAsc();
  List<LeaveType> findByActiveTrueOrderByNameAsc();
  Optional<LeaveType> findByCodeIgnoreCase(String code);
  boolean existsByCodeIgnoreCase(String code);
}
