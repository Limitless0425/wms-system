package com.example.demo.repository;

import com.example.demo.entity.OutboundKanban;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OutboundKanbanRepository extends JpaRepository<OutboundKanban, Long> {
    Optional<OutboundKanban> findByKanbanNo(String kanbanNo);
    List<OutboundKanban> findByOrderId(Long orderId);
    List<OutboundKanban> findByOrderNo(String orderNo);
}
