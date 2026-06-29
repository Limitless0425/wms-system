package com.example.demo.repository;

import com.example.demo.entity.RepackRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RepackRuleRepository extends JpaRepository<RepackRule, Long> {
    List<RepackRule> findBySupplierId(Long supplierId);
    Optional<RepackRule> findFirstBySupplierIdAndPartIdOrderByUpdateTimeDesc(Long supplierId, Long partId);
}
