package com.example.demo.repository;

import com.example.demo.entity.InventoryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InventoryRecordRepository extends JpaRepository<InventoryRecord, Long> {
    List<InventoryRecord> findByPartCode(String partCode);
    List<InventoryRecord> findByKanbanNo(String kanbanNo);
    List<InventoryRecord> findByRefOrderNo(String refOrderNo);
    List<InventoryRecord> findByKanbanNoAndRefOrderNo(String kanbanNo, String refOrderNo);
}
