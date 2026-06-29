package com.example.demo.repository;

import com.example.demo.entity.RepackKanban;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RepackKanbanRepository extends JpaRepository<RepackKanban, Long> {
    Optional<RepackKanban> findByKanbanNo(String kanbanNo);
    List<RepackKanban> findByOrderId(Long orderId);
    List<RepackKanban> findByOrderNo(String orderNo);
}
