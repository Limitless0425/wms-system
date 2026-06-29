package com.example.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "part")
public class Part {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;
    private String name;
    private String spec;
    private String unit;
    private Long supplierId;
    private String supplierName;
    private String customerBarcode;
    private Integer lowStock = 50;
    private Integer highStock = 0;
    private Integer originalPackageQty;
    private Integer targetPackageQty;
    private String repackContainerType;
    private LocalDateTime createTime;

    public Part() {}
    public Part(String code, String name, String spec, String unit, Long supplierId, String supplierName) {
        this.code = code; this.name = name; this.spec = spec; this.unit = unit;
        this.supplierId = supplierId; this.supplierName = supplierName; this.createTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public String getCustomerBarcode() { return customerBarcode; }
    public void setCustomerBarcode(String customerBarcode) { this.customerBarcode = customerBarcode; }
    public Integer getLowStock() { return lowStock; }
    public void setLowStock(Integer lowStock) { this.lowStock = lowStock; }
    public Integer getHighStock() { return highStock; }
    public void setHighStock(Integer highStock) { this.highStock = highStock; }
    public Integer getOriginalPackageQty() { return originalPackageQty; }
    public void setOriginalPackageQty(Integer originalPackageQty) { this.originalPackageQty = originalPackageQty; }
    public Integer getTargetPackageQty() { return targetPackageQty; }
    public void setTargetPackageQty(Integer targetPackageQty) { this.targetPackageQty = targetPackageQty; }
    public String getRepackContainerType() { return repackContainerType; }
    public void setRepackContainerType(String repackContainerType) { this.repackContainerType = repackContainerType; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
