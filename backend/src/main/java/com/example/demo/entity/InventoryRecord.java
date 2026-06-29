package com.example.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "inventory_record")
public class InventoryRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long partId;
    private String partCode;
    private String partName;
    private String unit;
    private String kanbanNo;
    private Long locationId;
    private String locationName;
    private Integer qty;
    private String type;
    private String refOrderNo;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPartId() { return partId; }
    public void setPartId(Long partId) { this.partId = partId; }
    public String getPartCode() { return partCode; }
    public void setPartCode(String partCode) { this.partCode = partCode; }
    public String getPartName() { return partName; }
    public void setPartName(String partName) { this.partName = partName; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getKanbanNo() { return kanbanNo; }
    public void setKanbanNo(String kanbanNo) { this.kanbanNo = kanbanNo; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getRefOrderNo() { return refOrderNo; }
    public void setRefOrderNo(String refOrderNo) { this.refOrderNo = refOrderNo; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}