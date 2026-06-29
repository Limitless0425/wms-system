package com.example.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "outbound_kanban")
public class OutboundKanban {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true)
    private String kanbanNo;
    private Long orderId;
    private String orderNo;
    private String customerName;
    private String outboundType;
    private String sourceKanbanNo;
    private Long partId;
    private String partCode;
    private String partName;
    private Integer actualQty;
    private String unit;
    private String supplierName;
    private String warehouseName;
    private String locationName;
    private String status;
    private LocalDateTime printTime;
    private LocalDateTime outboundTime;
    private String outboundOperator;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKanbanNo() { return kanbanNo; }
    public void setKanbanNo(String kanbanNo) { this.kanbanNo = kanbanNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getOutboundType() { return outboundType; }
    public void setOutboundType(String outboundType) { this.outboundType = outboundType; }
    public String getSourceKanbanNo() { return sourceKanbanNo; }
    public void setSourceKanbanNo(String sourceKanbanNo) { this.sourceKanbanNo = sourceKanbanNo; }
    public Long getPartId() { return partId; }
    public void setPartId(Long partId) { this.partId = partId; }
    public String getPartCode() { return partCode; }
    public void setPartCode(String partCode) { this.partCode = partCode; }
    public String getPartName() { return partName; }
    public void setPartName(String partName) { this.partName = partName; }
    public Integer getActualQty() { return actualQty; }
    public void setActualQty(Integer actualQty) { this.actualQty = actualQty; }
    public Integer getQty() { return actualQty; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getPrintTime() { return printTime; }
    public void setPrintTime(LocalDateTime printTime) { this.printTime = printTime; }
    public LocalDateTime getOutboundTime() { return outboundTime; }
    public void setOutboundTime(LocalDateTime outboundTime) { this.outboundTime = outboundTime; }
    public String getOutboundOperator() { return outboundOperator; }
    public void setOutboundOperator(String outboundOperator) { this.outboundOperator = outboundOperator; }
}
