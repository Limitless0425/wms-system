package com.example.demo.entity;

import jakarta.persistence.*;

@Entity @Table(name = "warehouse")
public class Warehouse {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;
    private String name;
    private String area;
    private Integer capacity;
    @Transient
    private Integer locationCapacity;
    @Transient
    private Integer remainingCapacity;
    @Transient
    private Boolean overCapacity;

    public Warehouse() {}
    public Warehouse(String code, String name, String area) {
        this.code = code; this.name = name; this.area = area; this.capacity = 0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public Integer getLocationCapacity() { return locationCapacity; }
    public void setLocationCapacity(Integer locationCapacity) { this.locationCapacity = locationCapacity; }
    public Integer getRemainingCapacity() { return remainingCapacity; }
    public void setRemainingCapacity(Integer remainingCapacity) { this.remainingCapacity = remainingCapacity; }
    public Boolean getOverCapacity() { return overCapacity; }
    public void setOverCapacity(Boolean overCapacity) { this.overCapacity = overCapacity; }
}
