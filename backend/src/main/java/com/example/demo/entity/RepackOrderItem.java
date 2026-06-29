package com.example.demo.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "repack_order_item")
public class RepackOrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String repackKanbanNo;
    private String sourceKanbanNo;
    private String sourceBusinessType;
    private String sourceContainerCode;
    private Long partId;
    private String partCode;
    private String partName;
    private String unit;
    private Integer planQty;
    private Integer actualQty;
    private String containerCode;
    private String containerName;
    private String targetContainerType;
    private Integer originalPackageQty;
    private Integer targetPackageQty;
    private Long warehouseId;
    private Long locationId;
    private String sourceWarehouseName;
    private String sourceLocationName;
    private String warehouseName;
    private String locationName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRepackKanbanNo() { return repackKanbanNo; }
    public void setRepackKanbanNo(String repackKanbanNo) { this.repackKanbanNo = repackKanbanNo; }
    public String getSourceKanbanNo() { return sourceKanbanNo; }
    public void setSourceKanbanNo(String sourceKanbanNo) { this.sourceKanbanNo = sourceKanbanNo; }
    public String getSourceBusinessType() { return sourceBusinessType; }
    public void setSourceBusinessType(String sourceBusinessType) { this.sourceBusinessType = sourceBusinessType; }
    public String getSourceContainerCode() { return sourceContainerCode; }
    public void setSourceContainerCode(String sourceContainerCode) { this.sourceContainerCode = sourceContainerCode; }
    public Long getPartId() { return partId; }
    public void setPartId(Long partId) { this.partId = partId; }
    public String getPartCode() { return partCode; }
    public void setPartCode(String partCode) { this.partCode = partCode; }
    public String getPartName() { return partName; }
    public void setPartName(String partName) { this.partName = partName; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Integer getPlanQty() { return planQty; }
    public void setPlanQty(Integer planQty) { this.planQty = planQty; }
    public Integer getActualQty() { return actualQty; }
    public void setActualQty(Integer actualQty) { this.actualQty = actualQty; }
    public String getContainerCode() { return containerCode; }
    public void setContainerCode(String containerCode) { this.containerCode = containerCode; }
    public String getContainerName() { return containerName; }
    public void setContainerName(String containerName) { this.containerName = containerName; }
    public String getTargetContainerType() { return targetContainerType; }
    public void setTargetContainerType(String targetContainerType) { this.targetContainerType = targetContainerType; }
    public Integer getOriginalPackageQty() { return originalPackageQty; }
    public void setOriginalPackageQty(Integer originalPackageQty) { this.originalPackageQty = originalPackageQty; }
    public Integer getTargetPackageQty() { return targetPackageQty; }
    public void setTargetPackageQty(Integer targetPackageQty) { this.targetPackageQty = targetPackageQty; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public String getSourceWarehouseName() { return sourceWarehouseName; }
    public void setSourceWarehouseName(String sourceWarehouseName) { this.sourceWarehouseName = sourceWarehouseName; }
    public String getSourceLocationName() { return sourceLocationName; }
    public void setSourceLocationName(String sourceLocationName) { this.sourceLocationName = sourceLocationName; }
    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
}
