package com.example.demo.entity;

import jakarta.persistence.*;

@Entity @Table(name = "location")
public class Location {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;
    private String name;
    private Long warehouseId;
    private String warehouseName;
    private Integer capacity;

    public Location() {}
    public Location(String code, String name, Long warehouseId, String warehouseName, Integer capacity) {
        this.code = code; this.name = name; this.warehouseId = warehouseId;
        this.warehouseName = warehouseName; this.capacity = capacity;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public String getWarehouseName() { return warehouseName; }
    public void setWarehouseName(String warehouseName) { this.warehouseName = warehouseName; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
}