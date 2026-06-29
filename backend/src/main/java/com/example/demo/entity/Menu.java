package com.example.demo.entity;

import java.util.List;

public class Menu {
    private String path;
    private String name;
    private String title;
    private String icon;
    private List<Menu> children;

    public Menu() {}
    public Menu(String path, String name, String title, String icon, List<Menu> children) {
        this.path = path;
        this.name = name;
        this.title = title;
        this.icon = icon;
        this.children = children;
    }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public List<Menu> getChildren() { return children; }
    public void setChildren(List<Menu> children) { this.children = children; }
}
