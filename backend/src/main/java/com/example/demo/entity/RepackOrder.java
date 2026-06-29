package com.example.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "repack_order")
public class RepackOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true)
    private String orderNo;
    private Long supplierId;
    private String supplierName;
    private String repackDirection;
    private Boolean allowBalance;
    private String sourceKanbanNo;
    private String sourceBusinessType;
    private String status;
    private String operator;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "repack_order_id")
    private List<RepackOrderItem> items = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public String getRepackDirection() { return repackDirection; }
    public void setRepackDirection(String repackDirection) { this.repackDirection = repackDirection; }
    public Boolean getAllowBalance() { return allowBalance; }
    public void setAllowBalance(Boolean allowBalance) { this.allowBalance = allowBalance; }
    public String getSourceKanbanNo() { return sourceKanbanNo; }
    public void setSourceKanbanNo(String sourceKanbanNo) { this.sourceKanbanNo = sourceKanbanNo; }
    public String getSourceBusinessType() { return sourceBusinessType; }
    public void setSourceBusinessType(String sourceBusinessType) { this.sourceBusinessType = sourceBusinessType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public List<RepackOrderItem> getItems() { return items; }
    public void setItems(List<RepackOrderItem> items) { this.items = items; }
}
