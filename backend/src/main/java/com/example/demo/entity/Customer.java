package com.example.demo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name = "customer")
public class Customer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;
    private String name;
    private String contact;
    private String phone;
    private String landline;
    private String level;
    private String address;
    private LocalDateTime createTime;

    public Customer() {}
    public Customer(String code, String name, String contact, String phone, String address) {
        this.code = code; this.name = name; this.contact = contact;
        this.phone = phone; this.address = address; this.createTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getLandline() { return landline; }
    public void setLandline(String landline) { this.landline = landline; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
