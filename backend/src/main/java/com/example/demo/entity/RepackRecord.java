package com.example.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "repack_record")
public class RepackRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String sourceKanbanNo;
    private String targetKanbanNo;
    private String partCode;
    private String partName;
    private Integer qty;
    private Integer sourceBalance;
    private String sourceContainerCode;
    private String targetContainerCode;
    private String operator;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceKanbanNo() { return sourceKanbanNo; }
    public void setSourceKanbanNo(String sourceKanbanNo) { this.sourceKanbanNo = sourceKanbanNo; }
    public String getTargetKanbanNo() { return targetKanbanNo; }
    public void setTargetKanbanNo(String targetKanbanNo) { this.targetKanbanNo = targetKanbanNo; }
    public String getPartCode() { return partCode; }
    public void setPartCode(String partCode) { this.partCode = partCode; }
    public String getPartName() { return partName; }
    public void setPartName(String partName) { this.partName = partName; }
    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
    public Integer getSourceBalance() { return sourceBalance; }
    public void setSourceBalance(Integer sourceBalance) { this.sourceBalance = sourceBalance; }
    public String getSourceContainerCode() { return sourceContainerCode; }
    public void setSourceContainerCode(String sourceContainerCode) { this.sourceContainerCode = sourceContainerCode; }
    public String getTargetContainerCode() { return targetContainerCode; }
    public void setTargetContainerCode(String targetContainerCode) { this.targetContainerCode = targetContainerCode; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
