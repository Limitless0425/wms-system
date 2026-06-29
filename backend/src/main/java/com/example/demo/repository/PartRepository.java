package com.example.demo.repository;

import com.example.demo.entity.Part;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PartRepository extends JpaRepository<Part, Long> {
    List<Part> findBySupplierId(Long supplierId);
    Optional<Part> findByCodeIgnoreCase(String code);
}
