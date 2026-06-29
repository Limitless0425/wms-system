package com.example.demo.repository;

import com.example.demo.entity.InboundOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InboundOrderRepository extends JpaRepository<InboundOrder, Long> {
    List<InboundOrder> findByStatus(String status);
    Optional<InboundOrder> findByOrderNo(String orderNo);
}