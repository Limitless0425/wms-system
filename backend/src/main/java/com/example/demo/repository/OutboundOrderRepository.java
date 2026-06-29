package com.example.demo.repository;

import com.example.demo.entity.OutboundOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OutboundOrderRepository extends JpaRepository<OutboundOrder, Long> {
    Optional<OutboundOrder> findByOrderNo(String orderNo);
}
