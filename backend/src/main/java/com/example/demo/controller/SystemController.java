package com.example.demo.controller;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final PasswordEncoder passwordEncoder;

    public SystemController(UserRepository userRepo, RoleRepository roleRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/users")
    public Map<String, Object> users(
            @RequestParam(defaultValue = "") String username,
            @RequestParam(defaultValue = "") String phone,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String createTimeStart,
            @RequestParam(defaultValue = "") String createTimeEnd) {
        List<User> list = new ArrayList<>(userRepo.findAll());
        if (!username.isBlank()) list.removeIf(u -> !contains(u.getDisplayName(), username) && !contains(u.getUsername(), username));
        if (!phone.isBlank()) list.removeIf(u -> !contains(u.getPhone(), phone));
        if ("enabled".equals(status)) list.removeIf(u -> !Boolean.TRUE.equals(u.getEnabled()));
        if ("disabled".equals(status)) list.removeIf(u -> !Boolean.FALSE.equals(u.getEnabled()));
        if (!createTimeStart.isBlank()) {
            LocalDateTime start = LocalDate.parse(createTimeStart).atStartOfDay();
            list.removeIf(u -> u.getCreateTime() == null || u.getCreateTime().isBefore(start));
        }
        if (!createTimeEnd.isBlank()) {
            LocalDateTime end = LocalDate.parse(createTimeEnd).atTime(LocalTime.MAX);
            list.removeIf(u -> u.getCreateTime() == null || u.getCreateTime().isAfter(end));
        }
        list.sort(Comparator.comparing(User::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return ok(list.stream().map(this::userView).collect(Collectors.toList()));
    }

    @PostMapping("/user")
    public Map<String, Object> addUser(@RequestBody Map<String, Object> body) {
        String username = text(body.get("username"));
        String password = text(body.get("password"));
        String role = text(body.get("role"));
        if (username.isBlank() || password.isBlank() || role.isBlank()) return fail("用户名、密码和角色不能为空");
        if (userRepo.findByUsername(username).isPresent()) return fail("用户名已存在");
        if (roleRepo.findByCode(role).isEmpty()) return fail("角色不存在");
        User user = new User(username, passwordEncoder.encode(password), role);
        user.setDisplayName(text(body.get("displayName")));
        user.setPhone(text(body.get("phone")));
        user.setEnabled(!Boolean.FALSE.equals(body.get("enabled")));
        return ok("用户创建成功", userView(userRepo.save(user)));
    }

    @PutMapping("/user")
    public Map<String, Object> updateUser(@RequestBody Map<String, Object> body) {
        Long id = number(body.get("id"));
        User user = id == null ? null : userRepo.findById(id).orElse(null);
        if (user == null) return fail("用户不存在");
        String role = text(body.get("role"));
        if (roleRepo.findByCode(role).isEmpty()) return fail("角色不存在");
        user.setDisplayName(text(body.get("displayName")));
        user.setPhone(text(body.get("phone")));
        user.setRole(role);
        user.setEnabled(!Boolean.FALSE.equals(body.get("enabled")));
        String password = text(body.get("password"));
        if (!password.isBlank()) user.setPassword(passwordEncoder.encode(password));
        return ok("用户修改成功", userView(userRepo.save(user)));
    }

    @DeleteMapping("/user/{id}")
    public Map<String, Object> deleteUser(@PathVariable Long id) {
        User user = userRepo.findById(id).orElse(null);
        if (user == null) return fail("用户不存在");
        if ("admin".equals(user.getUsername())) return fail("默认管理员不能删除");
        userRepo.delete(user);
        return ok("用户已删除", id);
    }

    @GetMapping("/roles")
    public Map<String, Object> roles() {
        return Map.of("code", 200, "data", roleRepo.findAll());
    }

    @PostMapping("/role")
    public Map<String, Object> addRole(@RequestBody Role role) {
        if (role.getCode() == null || role.getCode().isBlank() || role.getName() == null || role.getName().isBlank()) {
            return fail("角色编码和名称不能为空");
        }
        if (roleRepo.findByCode(role.getCode()).isPresent()) return fail("角色编码已存在");
        role.setId(null);
        role.setCode(role.getCode().trim().toUpperCase(Locale.ROOT));
        role.setCreateTime(LocalDateTime.now());
        return ok("角色创建成功", roleRepo.save(role));
    }

    @PutMapping("/role")
    public Map<String, Object> updateRole(@RequestBody Role role) {
        Role existing = role.getId() == null ? null : roleRepo.findById(role.getId()).orElse(null);
        if (existing == null) return fail("角色不存在");
        existing.setName(role.getName());
        existing.setPermissions(role.getPermissions());
        existing.setRemark(role.getRemark());
        return ok("角色修改成功", roleRepo.save(existing));
    }

    @DeleteMapping("/role/{id}")
    public Map<String, Object> deleteRole(@PathVariable Long id) {
        Role role = roleRepo.findById(id).orElse(null);
        if (role == null) return fail("角色不存在");
        if ("ADMIN".equals(role.getCode()) || "USER".equals(role.getCode())) return fail("系统预置角色不能删除");
        if (userRepo.countByRole(role.getCode()) > 0) return fail("该角色仍有关联用户，不能删除");
        roleRepo.delete(role);
        return ok("角色已删除", id);
    }

    private Map<String, Object> userView(User user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("displayName", user.getDisplayName());
        result.put("phone", user.getPhone());
        result.put("role", user.getRole());
        result.put("enabled", user.getEnabled());
        result.put("createTime", user.getCreateTime());
        return result;
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private Long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? null : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException e) { return null; }
    }
    private boolean contains(String value, String keyword) {
        return value != null && keyword != null && value.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT));
    }
    private Map<String, Object> fail(String message) { return Map.of("code", 400, "message", message); }
    private Map<String, Object> ok(String message, Object data) { return Map.of("code", 200, "message", message, "data", data); }
    private Map<String, Object> ok(Object data) { return Map.of("code", 200, "data", data); }
}
