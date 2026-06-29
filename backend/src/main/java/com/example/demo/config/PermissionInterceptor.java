package com.example.demo.config;

import com.example.demo.repository.RoleRepository;
import com.example.demo.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class PermissionInterceptor implements HandlerInterceptor {
    private final JwtUtil jwtUtil;
    private final RoleRepository roleRepo;

    public PermissionInterceptor(JwtUtil jwtUtil, RoleRepository roleRepo) {
        this.jwtUtil = jwtUtil;
        this.roleRepo = roleRepo;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String uri = request.getRequestURI();
        if (uri.equals("/api/login") || uri.equals("/api/userinfo") || uri.equals("/api/menus")) return true;

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) return true;
        String roleCode = jwtUtil.parseToken(auth.substring(7)).get("role", String.class);
        String configured = roleRepo.findByCode(roleCode).map(r -> r.getPermissions()).orElse("");
        Set<String> permissions = configured == null || configured.isBlank()
                ? Set.of() : new HashSet<>(Arrays.asList(configured.split(",")));
        if ("ADMIN".equals(roleCode) || permissions.contains("*")) return true;

        Set<String> required = requiredPermissions(uri);
        if (required.isEmpty() || required.stream().anyMatch(permissions::contains)) return true;

        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"当前角色没有此功能权限\"}");
        return false;
    }

    private Set<String> requiredPermissions(String uri) {
        if (uri.startsWith("/api/ai-admin")) return Set.of("ai.admin");
        if (uri.startsWith("/api/system/users") || uri.startsWith("/api/system/user")) return Set.of("system.user");
        if (uri.startsWith("/api/system/roles") || uri.startsWith("/api/system/role")) return Set.of("system.role");
        if (uri.startsWith("/api/outbound/orders") || uri.startsWith("/api/outbound/order")) return Set.of("outbound.order");
        if (uri.startsWith("/api/outbound/")) return Set.of("outbound.scan");
        if (uri.startsWith("/api/repack/")) return Set.of("operations.repack");
        if (uri.startsWith("/api/kanban/all")) return Set.of("kanban.manage");
        if (uri.startsWith("/api/kanban/repack")) return Set.of("operations.repack");
        if (uri.startsWith("/api/kanban/scan/")) return Set.of("inbound.scan", "outbound.scan", "operations.repack");
        if (uri.startsWith("/api/kanban/")) return Set.of("kanban.manage", "inbound.kanban", "inbound.order", "inbound.scan", "operations.repack");
        if (uri.startsWith("/api/inbound/")) return Set.of("inbound.order");
        if (uri.startsWith("/api/inventory/total-stock-report")) return Set.of("inventory.report", "inventory.stock");
        if (uri.startsWith("/api/inventory/stock") || uri.startsWith("/api/inventory/location")
                || uri.startsWith("/api/inventory/warehouse")) return Set.of("inventory.stock");
        if (uri.startsWith("/api/inventory/")) return Set.of("inventory.trace");
        if (uri.startsWith("/api/baseinfo/supplier")) return Set.of("baseinfo.supplier", "inbound.order", "kanban.manage", "inbound.kanban", "operations.repack");
        if (uri.startsWith("/api/baseinfo/customer")) return Set.of("baseinfo.customer", "outbound.order");
        if (uri.startsWith("/api/baseinfo/part")) return Set.of("baseinfo.part", "inbound.order", "kanban.manage", "inbound.kanban", "outbound.order");
        if (uri.startsWith("/api/baseinfo/location")) {
            return Set.of("baseinfo.location", "baseinfo.warehouse", "inbound.order", "kanban.manage", "inbound.kanban", "operations.repack");
        }
        if (uri.startsWith("/api/baseinfo/warehouse")) {
            return Set.of("baseinfo.warehouse", "inbound.order", "kanban.manage", "inbound.kanban", "operations.repack");
        }
        if (uri.startsWith("/api/baseinfo/container")) return Set.of("baseinfo.container", "inbound.order", "operations.repack");
        return Set.of();
    }
}
