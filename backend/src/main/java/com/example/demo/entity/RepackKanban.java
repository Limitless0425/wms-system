package com.example.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "repack_kanban")
public class RepackKanban {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true)
    private String kanbanNo;
    private Long orderId;
    private String orderNo;
    private String sourceKanbanNo;
    private String sourceBusinessType;
    private String targetKanbanNo;
    private Long partId;
    private String partCode;
    private String partName;
    private Integer qty;
    private String unit;
    private String supplierName;
    private String sourceContainerCode;
    private String targetContainerCode;
    private String targetContainerName;
    private String targetContainerType;
    private Integer targetPackageQty;
    private String warehouseName;
    private String locationName;
    private String sourceWarehouseName;
    private String sourceLocationName;
    private Integer actualQty;
    private Integer balanceQty;
    private String status;
    private LocalDateTime printTime;
    private LocalDateTime repackTime;
    private String operator;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKanbanNo() { return kanbanNo; }
    public void setKanbanNo(String kanbanNo) { this.kanbanNo = kanbanNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getSourceKanbanNo() { return sourceKanbanNo; }
    public void setSourceKanbanNo(String sourceKanbanNo) { this.sourceKanbanNo = sourceKanbanNo; }
    public String getSourceBusinessType() { return sourceBusinessType; }
    public void setSourceBusinessType(String sourceBusinessType) { this.sourceBusinessType = sourceBusinessType; }
    public String getTargetKanbanNo() { return targetKanbanNo; }
    public void setTargetKanbanNo(String targetKanbanNo) { this.targetKanbanNo = targetKanbanNo; }
    public Long getPartId() { return partId; }
    public void setPartId(Long partId) { this.partId = partId; }
    public String getPartCode() { return partCode; }
    public void setPartCode(String partCode) { this.partCode = partCode; }
    public String getPartName() { return partName; }
    public void setPartName(String partName) { this.partName = partName; }
    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public String getSourceContainerCode() { return sourceContainerCode; }
    public void setSourceContainerCode(String sourceContainerCode) { this.sourceContainerCode = sourceContainerCode; }
    public String getTargetContainerCode() { return targetContainerCode; }
    public void setTargetContainerCode(String targetContainerCode) { this.targetContainerCode = targetContainerCode; }
    public String getTargetContainerName() { return targetContainerName; }
    public void setTargetContainerName(String targetContainerName) { this.targetContainerName = targetContainerName; }
    public String getTargetContainerType() { return targetContainerType; }
    public void setTargetContainerType(String targetContainerType) { this.targetContainerType = targetContainerType; }
    public Integer getTargetPackageQty() { return targetPackageQty; }
    public void setTargetPackageQty(Integer targetPackageQty) { this.targetPackageQty = targetPackageQty; }
    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public String getSourceWarehouseName() { return sourceWarehouseName; }
    public void setSourceWarehouseName(String sourceWarehouseName) { this.sourceWarehouseName = sourceWarehouseName; }
    public String getSourceLocationName() { return sourceLocationName; }
    public void setSourceLocationName(String sourceLocationName) { this.sourceLocationName = sourceLocationName; }
    public Integer getActualQty() { return actualQty; }
    public void setActualQty(Integer actualQty) { this.actualQty = actualQty; }
    public Integer getBalanceQty() { return balanceQty; }
    public void setBalanceQty(Integer balanceQty) { this.balanceQty = balanceQty; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getPrintTime() { return printTime; }
    public void setPrintTime(LocalDateTime printTime) { this.printTime = printTime; }
    public LocalDateTime getRepackTime() { return repackTime; }
    public void setRepackTime(LocalDateTime repackTime) { this.repackTime = repackTime; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
