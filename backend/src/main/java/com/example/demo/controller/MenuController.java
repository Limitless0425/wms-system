package com.example.demo.controller;

import com.example.demo.entity.Menu;
import com.example.demo.repository.RoleRepository;
import com.example.demo.util.JwtUtil;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class MenuController {
    private final JwtUtil jwtUtil;
    private final RoleRepository roleRepo;

    public MenuController(JwtUtil jwtUtil, RoleRepository roleRepo) {
        this.jwtUtil = jwtUtil;
        this.roleRepo = roleRepo;
    }

    @GetMapping("/menus")
    public Map<String, Object> getMenus(@RequestHeader("Authorization") String authHeader) {
        var claims = jwtUtil.parseToken(authHeader.substring(7));
        String roleCode = claims.get("role", String.class);
        String configured = roleRepo.findByCode(roleCode).map(r -> r.getPermissions()).orElse("");
        Set<String> permissions = configured == null || configured.isBlank()
                ? Set.of() : new HashSet<>(Arrays.asList(configured.split(",")));
        boolean all = "ADMIN".equals(roleCode) || permissions.contains("*");

        List<Menu> menus = buildMenus();
        if (!all) {
            menus = menus.stream().map(group -> {
                if (group.getChildren() == null) return group;
                List<Menu> children = group.getChildren().stream()
                        .filter(item -> permissions.contains(permissionFor(item.getPath())))
                        .toList();
                return new Menu(group.getPath(), group.getName(), group.getTitle(), group.getIcon(), new ArrayList<>(children));
            }).filter(group -> group.getChildren() == null || !group.getChildren().isEmpty()).toList();
        }
        return Map.of("code", 200, "data", menus);
    }

    private List<Menu> buildMenus() {
        List<Menu> menus = new ArrayList<>();
        menus.add(group("/inbound", "Inbound", "\u5165\u5E93\u7BA1\u7406", "Box", List.of(
                item("/inbound/order", "InboundOrder", "\u5165\u5E93\u5355\u7BA1\u7406", "Tickets"),
                item("/inbound/scan", "KanbanScan", "\u626B\u7801\u5165\u5E93", "Scan")
        )));
        menus.add(group("/kanban", "Kanban", "\u770B\u677F\u7BA1\u7406", "Grid", List.of(
                item("/kanban/manage", "KanbanManage", "\u770B\u677F\u7BA1\u7406", "Grid")
        )));
        menus.add(group("/operations", "Operations", "\u4ED3\u50A8\u4F5C\u4E1A", "Operation", List.of(
                item("/outbound/order", "OutboundOrder", "\u51FA\u5E93\u5355\u7BA1\u7406", "Document"),
                item("/outbound/scan", "OutboundScan", "\u5E26\u5355\u51FA\u5E93", "Position"),
                item("/outbound/direct-scan", "DirectOutboundScan", "\u4E0D\u5E26\u5355\u51FA\u5E93", "Scan"),
                item("/operations/repack", "RepackManage", "\u8F6C\u5305\u5355\u7BA1\u7406", "Refresh"),
                item("/operations/repack-scan", "RepackScan", "\u8F6C\u5305\u4F5C\u4E1A", "Scan"),
                item("/operations/repack-balance", "RepackBalance", "\u8F6C\u5305\u7ED3\u4F59", "DataLine")
        )));
        menus.add(group("/baseinfo", "BaseInfo", "\u57FA\u7840\u4FE1\u606F", "Setting", List.of(
                item("/baseinfo/supplier", "SupplierInfo", "\u4F9B\u5E94\u5546\u7BA1\u7406", "OfficeBuilding"),
                item("/baseinfo/customer", "CustomerInfo", "\u5BA2\u6237\u7BA1\u7406", "Van"),
                item("/baseinfo/part", "PartInfo", "\u96F6\u4EF6\u7BA1\u7406", "Goods"),
                item("/baseinfo/warehouse", "WarehouseInfo", "\u4ED3\u5E93\u7BA1\u7406", "HomeFilled"),
                item("/baseinfo/location", "LocationInfo", "\u5E93\u4F4D\u7BA1\u7406", "Location"),
                item("/baseinfo/container", "ContainerInfo", "\u5668\u5177\u7BA1\u7406", "Box")
        )));
        menus.add(group("/inventory", "Inventory", "\u5E93\u5B58\u7BA1\u7406", "TrendCharts", List.of(
                item("/inventory/stock", "StockSummary", "\u5E93\u5B58\u76D1\u63A7", "DataAnalysis"),
                item("/inventory/report", "InventoryReport", "\u603B\u5E93\u5B58\u62A5\u8868", "TrendCharts"),
                item("/inventory/trace", "InventoryTrace", "\u5E93\u5B58\u8FFD\u6EAF", "Search")
        )));
        menus.add(group("/ai", "AiAdmin", "\u0041\u0049\u4ED3\u5E93\u7BA1\u7406\u5458", "Monitor", List.of(
                item("/ai/admin", "AiWarehouseAdmin", "\u0041\u0049\u4ED3\u5E93\u7BA1\u7406\u5458", "ChatDotRound")
        )));
        menus.add(group("/system", "System", "\u7CFB\u7EDF\u7BA1\u7406", "Setting", List.of(
                item("/system/user", "UserManage", "\u7528\u6237\u7BA1\u7406", "User"),
                item("/system/role", "RoleManage", "\u89D2\u8272\u6743\u9650", "UserFilled")
        )));
        return menus;
    }

    private String permissionFor(String path) {
        if ("/ai/admin".equals(path)) return "ai.admin";
        if ("/outbound/direct-scan".equals(path)) return "outbound.scan";
        if ("/operations/repack-balance".equals(path) || "/operations/repack-scan".equals(path)) return "operations.repack";
        return path.startsWith("/") ? path.substring(1).replace('/', '.') : path.replace('/', '.');
    }
    private Menu item(String path, String name, String title, String icon) {
        return new Menu(path, name, title, icon, null);
    }
    private Menu group(String path, String name, String title, String icon, List<Menu> children) {
        return new Menu(path, name, title, icon, new ArrayList<>(children));
    }
}
