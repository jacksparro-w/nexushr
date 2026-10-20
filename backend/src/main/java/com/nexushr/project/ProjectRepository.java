package com.nexushr.project;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
  List<Project> findAllByOrderByNameAsc();
  boolean existsByCodeIgnoreCase(String code);
  boolean existsByNameIgnoreCase(String name);
}
