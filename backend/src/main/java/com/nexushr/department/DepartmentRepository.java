package com.nexushr.department;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
  List<Department> findAllByOrderByNameAsc();
  boolean existsByCodeIgnoreCase(String code);
  boolean existsByNameIgnoreCase(String name);
  boolean existsByManagerId(UUID managerId);
}
