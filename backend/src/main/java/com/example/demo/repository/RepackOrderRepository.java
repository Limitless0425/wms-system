package com.example.demo.repository;

import com.example.demo.entity.RepackOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RepackOrderRepository extends JpaRepository<RepackOrder, Long> {
    Optional<RepackOrder> findByOrderNo(String orderNo);
}
