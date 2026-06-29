package com.example.demo.repository;

import com.example.demo.entity.Kanban;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface KanbanRepository extends JpaRepository<Kanban, Long> {
    Optional<Kanban> findByKanbanNo(String kanbanNo);
    long countByOrderIdAndStatus(Long orderId, String status);
    List<Kanban> findByOrderId(Long orderId);
    List<Kanban> findByPartIdAndStatus(Long partId, String status);
    void deleteByOrderIdAndStatus(Long orderId, String status);
}
