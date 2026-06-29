package com.example.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "kanban")
public class Kanban {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true)
    private String kanbanNo;
    private Long orderId;
    private String orderNo;
    private Long partId;
    private String partCode;
    private String partName;
    private Integer qty;
    private String unit;
    private String locationName;
    private String warehouseName;
    private String containerCode;
    private String containerName;
    private String status;
    private LocalDateTime printTime;
    private LocalDateTime scanTime;
    private String scanner;
    private Boolean sealed;
    private LocalDateTime sealTime;
    private String sealReason;
    private LocalDateTime outboundTime;
    private String outboundOperator;
    private String outboundOrderNo;
    private String sourceKanbanNo;
    @Transient
    private String supplierName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKanbanNo() { return kanbanNo; }
    public void setKanbanNo(String kanbanNo) { this.kanbanNo = kanbanNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
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
    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
    public String getContainerCode() { return containerCode; }
    public void setContainerCode(String containerCode) { this.containerCode = containerCode; }
    public String getContainerName() { return containerName; }
    public void setContainerName(String containerName) { this.containerName = containerName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getPrintTime() { return printTime; }
    public void setPrintTime(LocalDateTime printTime) { this.printTime = printTime; }
    public LocalDateTime getScanTime() { return scanTime; }
    public void setScanTime(LocalDateTime scanTime) { this.scanTime = scanTime; }
    public String getScanner() { return scanner; }
    public void setScanner(String scanner) { this.scanner = scanner; }
    public Boolean getSealed() { return sealed; }
    public void setSealed(Boolean sealed) { this.sealed = sealed; }
    public LocalDateTime getSealTime() { return sealTime; }
    public void setSealTime(LocalDateTime sealTime) { this.sealTime = sealTime; }
    public String getSealReason() { return sealReason; }
    public void setSealReason(String sealReason) { this.sealReason = sealReason; }
    public LocalDateTime getOutboundTime() { return outboundTime; }
    public void setOutboundTime(LocalDateTime outboundTime) { this.outboundTime = outboundTime; }
    public String getOutboundOperator() { return outboundOperator; }
    public void setOutboundOperator(String outboundOperator) { this.outboundOperator = outboundOperator; }
    public String getOutboundOrderNo() { return outboundOrderNo; }
    public void setOutboundOrderNo(String outboundOrderNo) { this.outboundOrderNo = outboundOrderNo; }
    public String getSourceKanbanNo() { return sourceKanbanNo; }
    public void setSourceKanbanNo(String sourceKanbanNo) { this.sourceKanbanNo = sourceKanbanNo; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
}
