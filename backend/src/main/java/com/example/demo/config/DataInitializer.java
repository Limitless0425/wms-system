package com.example.demo.config;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDateTime;

@Component
public class DataInitializer implements CommandLineRunner {

    private final SupplierRepository supplierRepo;
    private final PartRepository partRepo;
    private final WarehouseRepository warehouseRepo;
    private final LocationRepository locationRepo;
    private final CustomerRepository customerRepo;
    private final ContainerRepository containerRepo;
    private final RepackRuleRepository repackRuleRepo;
    private final InboundOrderRepository orderRepo;
    private final RepackOrderRepository repackOrderRepo;
    private final RepackKanbanRepository repackKanbanRepo;
    private final KanbanRepository kanbanRepo;
    private final RepackRecordRepository repackRecordRepo;
    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SupplierRepository s, PartRepository p, WarehouseRepository w,
                           LocationRepository l, CustomerRepository c, ContainerRepository ct,
                           InboundOrderRepository o, RepackOrderRepository ro, RepackKanbanRepository rk,
                           KanbanRepository k, RepackRecordRepository rr, UserRepository u, RoleRepository r,
                           PasswordEncoder passwordEncoder, RepackRuleRepository repackRuleRepo) {
        this.supplierRepo = s; this.partRepo = p; this.warehouseRepo = w;
        this.locationRepo = l; this.customerRepo = c; this.containerRepo = ct;
        this.repackRuleRepo = repackRuleRepo;
        this.orderRepo = o;
        this.repackOrderRepo = ro; this.repackKanbanRepo = rk;
        this.kanbanRepo = k; this.repackRecordRepo = rr;
        this.userRepo = u; this.roleRepo = r; this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        initializeSecurity();
        initializeMasterData();
        migrateRepackRulesToPartSettings();
        initializePartSettings();
        repairCorruptedInboundOrders();
        cleanupBadGuangzhouDensoRepackData();
    }

    private void initializeMasterData() {
        var wh1 = upsertWarehouse("WH01", "佛山一厂原材料仓", "A区");
        var wh2 = upsertWarehouse("WH02", "广州二厂总装仓", "B区");
        var wh3 = upsertWarehouse("WH03", "深圳电子件恒温仓", "C区");
        var wh4 = upsertWarehouse("WH04", "惠州转包暂存仓", "D区");

        ensureLocation("A-01-01", wh1, 500);
        ensureLocation("A-01-02", wh1, 500);
        ensureLocation("A-02-01", wh1, 500);
        ensureLocation("B-01-01", wh2, 800);
        ensureLocation("B-01-02", wh2, 800);
        ensureLocation("C-01-01", wh3, 300);
        ensureLocation("C-01-02", wh3, 300);
        ensureLocation("D-01-01", wh4, 600);

        var s1 = upsertSupplier("SUP001", "佛山博世汽车部件", "张经理", "13800001111", "0757-8001001", "A", "佛山南海区", wh1);
        var s2 = upsertSupplier("SUP002", "广州电装有限公司", "李主管", "13800002222", "020-8002002", "A", "广州黄埔区", wh2);
        var s3 = upsertSupplier("SUP003", "深圳法雷奥电子", "陈经理", "13800005555", "0755-8003003", "B", "深圳龙岗区", wh3);
        var s4 = upsertSupplier("SUP004", "惠州住友线束", "刘主管", "13800006666", "0752-8004004", "B", "惠州仲恺区", wh4);
        var s5 = upsertSupplier("SUP005", "东莞大陆制动系统", "黄经理", "13800007777", "0769-8005005", "A", "东莞松山湖", wh1);

        upsertPart("PT001", "刹车片总成", "350x120mm", "个", s1, 50, 500);
        upsertPart("PT002", "机油滤清器", "D80x100mm", "个", s1, 80, 800);
        upsertPart("PT003", "空调压缩机", "12V/24V通用", "台", s2, 30, 300);
        upsertPart("PT004", "点火线圈", "BOSCH-0986", "个", s2, 60, 600);
        upsertPart("PT005", "车身控制模块", "BCM-12V", "个", s3, 20, 200);
        upsertPart("PT006", "倒车雷达传感器", "18mm 黑色", "个", s3, 100, 1000);
        upsertPart("PT007", "主线束总成", "前舱线束", "套", s4, 25, 250);
        upsertPart("PT008", "门线束组件", "左前门", "套", s4, 40, 400);
        upsertPart("PT009", "制动卡钳", "前轮左侧", "个", s5, 35, 350);
        upsertPart("PT010", "制动软管", "EPDM 420mm", "根", s5, 120, 1200);
        upsertPart("PT011", "ABS传感器", "前轮主动式", "个", s1, 80, 800);
        upsertPart("PT012", "燃油滤清器", "高压管路型", "个", s1, 70, 700);
        upsertPart("PT013", "雨刮电机", "12V 左舵", "台", s1, 30, 300);
        upsertPart("PT014", "冷凝器总成", "铝制平行流", "台", s2, 25, 250);
        upsertPart("PT015", "鼓风机总成", "12V 自动空调", "台", s2, 35, 350);
        upsertPart("PT016", "氧传感器", "四线制", "个", s2, 90, 900);
        upsertPart("PT017", "毫米波雷达", "77GHz 前向", "个", s3, 10, 100);
        upsertPart("PT018", "胎压监测模块", "433MHz", "个", s3, 120, 1200);
        upsertPart("PT019", "车载摄像头", "广角高清", "个", s3, 60, 600);
        upsertPart("PT020", "仪表板线束", "IP Harness", "套", s4, 20, 200);
        upsertPart("PT021", "后备箱线束", "Tail Harness", "套", s4, 35, 350);
        upsertPart("PT022", "高压连接线", "橙色屏蔽线", "根", s4, 50, 500);
        upsertPart("PT023", "制动盘", "前轮通风盘", "个", s5, 45, 450);
        upsertPart("PT024", "电子驻车执行器", "EPB 左侧", "个", s5, 30, 300);
        upsertPart("PT025", "真空助力器", "9英寸", "台", s5, 20, 200);

        // 佛山博世汽车部件 新增25个零件 (PT026-PT050)
        upsertPart("PT026", "火花塞总成", "NGK-IR铱金", "个", s1, 200, 2000);
        upsertPart("PT027", "空调滤清器", "活性炭PM2.5", "个", s1, 150, 1500);
        upsertPart("PT028", "空气滤清器", "高流量型", "个", s1, 160, 1600);
        upsertPart("PT029", "发动机皮带", "6PK多楔带", "根", s1, 100, 1000);
        upsertPart("PT030", "涨紧轮总成", "自动涨紧型", "个", s1, 80, 800);
        upsertPart("PT031", "水泵总成", "离心式", "台", s1, 40, 400);
        upsertPart("PT032", "节温器总成", "电子控制型", "个", s1, 90, 900);
        upsertPart("PT033", "散热器总成", "铝制平行流", "台", s1, 25, 250);
        upsertPart("PT034", "转向机总成", "电动助力EPS", "台", s1, 15, 150);
        upsertPart("PT035", "转向拉杆球头", "M14外螺纹", "个", s1, 120, 1200);
        upsertPart("PT036", "减震器总成", "前轮麦弗逊", "根", s1, 60, 600);
        upsertPart("PT037", "稳定杆连杆", "前稳定杆", "根", s1, 140, 1400);
        upsertPart("PT038", "控制臂总成", "前下控制臂", "个", s1, 45, 450);
        upsertPart("PT039", "轮毂轴承总成", "三代轮毂单元", "个", s1, 70, 700);
        upsertPart("PT040", "传动半轴总成", "前轴左侧", "根", s1, 30, 300);
        upsertPart("PT041", "离合器从动盘", "D210mm", "个", s1, 55, 550);
        upsertPart("PT042", "分离轴承", "液压式", "个", s1, 85, 850);
        upsertPart("PT043", "变速器油封", "氟橡胶型", "个", s1, 180, 1800);
        upsertPart("PT044", "排气管吊耳", "耐热橡胶", "个", s1, 200, 2000);
        upsertPart("PT045", "三元催化器", "欧六标准", "台", s1, 10, 100);
        upsertPart("PT046", "碳罐电磁阀", "燃油蒸发控制", "个", s1, 110, 1100);
        upsertPart("PT047", "进气歧管总成", "可变长度型", "台", s1, 20, 200);
        upsertPart("PT048", "节气门体总成", "电子节气门", "台", s1, 35, 350);
        upsertPart("PT049", "机油泵总成", "可变排量型", "台", s1, 25, 250);
        upsertPart("PT050", "正时链条套件", "静音链型", "套", s1, 50, 500);

        if (customerRepo.count() == 0) {
            customerRepo.save(new Customer("CUS001", "一汽-大众佛山工厂", "王工", "13800003333", "佛山南海区狮山镇"));
            customerRepo.save(new Customer("CUS002", "一汽-大众长春工厂", "赵主任", "13800004444", "长春市绿园区"));
        }
        if (containerRepo.count() == 0) {
            containerRepo.save(new Container("CTN001", "标准塑料箱A", "普通器具", "600x400x300mm", 50));
            containerRepo.save(new Container("CTN002", "标准塑料箱B", "普通器具", "400x300x200mm", 30));
            containerRepo.save(new Container("CTN003", "铁质料架", "普通器具", "1200x1000x800mm", 200));
            containerRepo.save(new Container("CTN004", "转包专用箱C", "转包器具", "800x600x400mm", 100));
            containerRepo.save(new Container("CTN005", "转包托盘D", "转包器具", "1200x1000mm", 500));
        }
        ensureContainer("RPK010", "转包小件盒10", "转包器具", "400x300x150mm", 10);
        ensureContainer("RPK020", "转包周转箱20", "转包器具", "500x350x200mm", 20);
        ensureContainer("RPK030", "转包周转箱30", "转包器具", "600x400x220mm", 30);
        ensureContainer("RPK050", "转包标准箱50", "转包器具", "600x400x300mm", 50);
        ensureContainer("RPK100", "转包大箱100", "转包器具", "800x600x400mm", 100);
        ensurePartRepackSetting("PT001", 100, 20, "转包器具");
        ensurePartRepackSetting("PT002", 120, 30, "转包器具");
        ensurePartRepackSetting("PT003", 100, 20, "转包器具");
        ensurePartRepackSetting("PT007", 50, 10, "转包器具");
    }

    private Warehouse upsertWarehouse(String code, String name, String area) {
        var warehouse = warehouseRepo.findByCodeIgnoreCase(code).orElseGet(Warehouse::new);
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setArea(area);
        if (warehouse.getCapacity() == null) warehouse.setCapacity(0);
        return warehouseRepo.save(warehouse);
    }

    private void ensureLocation(String code, Warehouse warehouse, int capacity) {
        if (locationRepo.existsByCodeIgnoreCase(code)) return;
        locationRepo.save(new Location(code, code, warehouse.getId(), warehouse.getName(), capacity));
    }

    private Supplier upsertSupplier(String code, String name, String contact, String phone,
                                    String landline, String level, String address, Warehouse warehouse) {
        var supplier = supplierRepo.findByCodeIgnoreCase(code).orElseGet(Supplier::new);
        supplier.setCode(code);
        supplier.setName(name);
        supplier.setContact(contact);
        supplier.setPhone(phone);
        supplier.setLandline(landline);
        supplier.setLevel(level);
        supplier.setAddress(address);
        supplier.setPreferredWarehouseId(warehouse.getId());
        supplier.setPreferredWarehouseName(warehouse.getName());
        if (supplier.getCreateTime() == null) supplier.setCreateTime(LocalDateTime.now());
        return supplierRepo.save(supplier);
    }

    private void upsertPart(String code, String name, String spec, String unit,
                            Supplier supplier, int lowStock, int highStock) {
        var part = partRepo.findByCodeIgnoreCase(code).orElseGet(Part::new);
        part.setCode(code);
        part.setName(name);
        part.setSpec(spec);
        part.setUnit(unit);
        part.setSupplierId(supplier.getId());
        part.setSupplierName(supplier.getName());
        part.setLowStock(lowStock);
        part.setHighStock(highStock);
        if (part.getCreateTime() == null) part.setCreateTime(LocalDateTime.now());
        partRepo.save(part);
    }

    private void ensurePartRepackSetting(String partCode, int originalQty, int targetQty, String containerType) {
        var part = partRepo.findByCodeIgnoreCase(partCode).orElse(null);
        if (part == null) return;
        boolean changed = false;
        if (part.getOriginalPackageQty() == null || part.getOriginalPackageQty() <= 0) {
            part.setOriginalPackageQty(originalQty);
            changed = true;
        }
        if (part.getTargetPackageQty() == null || part.getTargetPackageQty() <= 0) {
            part.setTargetPackageQty(targetQty);
            changed = true;
        }
        if (part.getRepackContainerType() == null || part.getRepackContainerType().isBlank()) {
            part.setRepackContainerType(containerType);
            changed = true;
        }
        if (changed) partRepo.save(part);
    }

    private void ensureContainer(String code, String name, String type, String spec, int capacity) {
        var existing = containerRepo.findAll().stream()
                .filter(container -> code.equalsIgnoreCase(container.getCode()))
                .findFirst().orElse(null);
        Container container = existing == null ? new Container() : existing;
        container.setCode(code);
        container.setName(name);
        container.setType(type);
        container.setSpec(spec);
        container.setCapacity(capacity);
        if (container.getCreateTime() == null) container.setCreateTime(LocalDateTime.now());
        containerRepo.save(container);
    }

    private void migrateRepackRulesToPartSettings() {
        for (var rule : repackRuleRepo.findAll()) {
            if (rule.getPartId() == null) continue;
            var part = partRepo.findById(rule.getPartId()).orElse(null);
            if (part == null) continue;
            boolean changed = false;
            if (part.getOriginalPackageQty() == null || part.getOriginalPackageQty() <= 0) {
                part.setOriginalPackageQty(rule.getOriginalPackageQty());
                changed = true;
            }
            if (part.getTargetPackageQty() == null || part.getTargetPackageQty() <= 0) {
                part.setTargetPackageQty(rule.getTargetPackageQty());
                changed = true;
            }
            if ((part.getRepackContainerType() == null || part.getRepackContainerType().isBlank())
                    && rule.getContainerType() != null && !rule.getContainerType().isBlank()) {
                part.setRepackContainerType(rule.getContainerType());
                changed = true;
            }
            if (changed) partRepo.save(part);
        }
    }

    private void initializeSecurity() {
        if (roleRepo.findByCode("ADMIN").isEmpty()) {
            var adminRole = new Role();
            adminRole.setCode("ADMIN");
            adminRole.setName("系统管理员");
            adminRole.setPermissions("*");
            adminRole.setRemark("拥有全部菜单和功能权限");
            adminRole.setCreateTime(LocalDateTime.now());
            roleRepo.save(adminRole);
        }
        if (roleRepo.findByCode("USER").isEmpty()) {
            var userRole = new Role();
            userRole.setCode("USER");
            userRole.setName("仓库操作员");
            userRole.setPermissions("inbound.order,kanban.manage,inbound.kanban,inbound.scan,outbound.order,outbound.scan,operations.repack,inventory.stock,inventory.report,inventory.trace,ai.admin");
            userRole.setRemark("执行日常入库、出库、转包和库存查询");
            userRole.setCreateTime(LocalDateTime.now());
            roleRepo.save(userRole);
        } else {
            roleRepo.findByCode("USER").ifPresent(role -> {
                String permissions = role.getPermissions() == null ? "" : role.getPermissions();
                if (!permissions.contains("kanban.manage") && !"*".equals(permissions)) {
                    role.setPermissions(permissions.isBlank() ? "kanban.manage" : permissions + ",kanban.manage");
                    roleRepo.save(role);
                }
                if (!permissions.contains("inventory.report") && !"*".equals(permissions)) {
                    role.setPermissions((role.getPermissions() == null || role.getPermissions().isBlank())
                            ? "inventory.report" : role.getPermissions() + ",inventory.report");
                    roleRepo.save(role);
                }
                if (!permissions.contains("ai.admin") && !"*".equals(permissions)) {
                    role.setPermissions(role.getPermissions() + ",ai.admin");
                    roleRepo.save(role);
                }
            });
        }
        if (userRepo.findByUsername("admin").isEmpty()) {
            var admin = new User("admin", passwordEncoder.encode("admin123"), "ADMIN");
            admin.setDisplayName("系统管理员");
            userRepo.save(admin);
        }
        if (roleRepo.findByCode("AI_ADMIN").isEmpty()) {
            var aiRole = new Role();
            aiRole.setCode("AI_ADMIN");
            aiRole.setName("AI仓库管理员");
            aiRole.setPermissions("*");
            aiRole.setRemark("可执行所有仓库业务操作，并使用AI仓库管理员进行库存问答、追溯和异常检查");
            aiRole.setCreateTime(LocalDateTime.now());
            roleRepo.save(aiRole);
        }
        if (userRepo.findByUsername("ai_admin").isEmpty()) {
            var aiAdmin = new User("ai_admin", passwordEncoder.encode("ai123456"), "AI_ADMIN");
            aiAdmin.setDisplayName("AI仓库管理员");
            userRepo.save(aiAdmin);
        }
        if (userRepo.findByUsername("user").isEmpty()) {
            var user = new User("user", passwordEncoder.encode("user123"), "USER");
            user.setDisplayName("仓库操作员");
            userRepo.save(user);
        }
        roleRepo.findByCode("USER").ifPresent(role -> ensureRolePermissions(role,
                "kanban.manage", "inventory.report", "ai.admin"));
    }

    private void ensureRolePermissions(Role role, String... required) {
        if (role == null || "*".equals(role.getPermissions())) return;
        String permissions = role.getPermissions() == null ? "" : role.getPermissions();
        boolean changed = false;
        for (String permission : required) {
            if (permission == null || permission.isBlank()) continue;
            if (!permissions.contains(permission)) {
                permissions = permissions.isBlank() ? permission : permissions + "," + permission;
                changed = true;
            }
        }
        if (changed) {
            role.setPermissions(permissions);
            roleRepo.save(role);
        }
    }

    private void initializePartSettings() {
        for (var part : partRepo.findAll()) {
            boolean changed = false;
            if (part.getLowStock() == null) {
                part.setLowStock(50);
                changed = true;
            }
            if (part.getHighStock() == null) {
                part.setHighStock(0);
                changed = true;
            }
            if (part.getOriginalPackageQty() == null || part.getOriginalPackageQty() <= 0) {
                part.setOriginalPackageQty(defaultOriginalPackageQty(part));
                changed = true;
            }
            if (part.getTargetPackageQty() == null || part.getTargetPackageQty() <= 0) {
                part.setTargetPackageQty(defaultTargetPackageQty(part));
                changed = true;
            }
            if (part.getRepackContainerType() == null || part.getRepackContainerType().isBlank()) {
                part.setRepackContainerType("转包器具");
                changed = true;
            }
            if (changed) partRepo.save(part);
        }
    }

    private int defaultOriginalPackageQty(Part part) {
        int high = part.getHighStock() == null || part.getHighStock() <= 0 ? 500 : part.getHighStock();
        if (high <= 150) return 50;
        if (high <= 400) return 100;
        if (high <= 900) return 120;
        return 200;
    }

    private int defaultTargetPackageQty(Part part) {
        int original = part.getOriginalPackageQty() == null || part.getOriginalPackageQty() <= 0
                ? defaultOriginalPackageQty(part) : part.getOriginalPackageQty();
        if (original <= 50) return 10;
        if (original <= 100) return 20;
        if (original <= 120) return 30;
        return 50;
    }

    private void repairCorruptedInboundOrders() {
        for (var order : orderRepo.findAll()) {
            boolean changed = false;
            var supplier = order.getSupplierId() == null ? null : supplierRepo.findById(order.getSupplierId()).orElse(null);
            var warehouse = order.getWarehouseId() == null ? null : warehouseRepo.findById(order.getWarehouseId()).orElse(null);
            if (isCorrupted(order.getSupplierName()) && supplier != null) {
                order.setSupplierName(supplier.getName());
                changed = true;
            }
            if (isCorrupted(order.getWarehouseName()) && warehouse != null) {
                order.setWarehouseName(warehouse.getName());
                changed = true;
            }
            if (isCorrupted(order.getInboundType())) {
                order.setInboundType("正常入库");
                changed = true;
            }
            for (var item : order.getItems()) {
                var part = item.getPartId() == null ? null : partRepo.findById(item.getPartId()).orElse(null);
                if (part != null) {
                    if (isCorrupted(item.getPartName())) { item.setPartName(part.getName()); changed = true; }
                    if (isCorrupted(item.getUnit())) { item.setUnit(part.getUnit()); changed = true; }
                }
            }
            if (changed) orderRepo.save(order);
        }
    }

    private void cleanupBadGuangzhouDensoRepackData() {
        var badOrders = repackOrderRepo.findAll().stream()
                .filter(order -> "广州电装有限公司".equals(order.getSupplierName()))
                .toList();
        if (badOrders.isEmpty()) return;

        var badOrderNos = badOrders.stream().map(RepackOrder::getOrderNo).collect(java.util.stream.Collectors.toSet());
        var badOrderIds = badOrders.stream().map(RepackOrder::getId).collect(java.util.stream.Collectors.toSet());
        var badRepackKanbans = repackKanbanRepo.findAll().stream()
                .filter(kanban -> badOrderNos.contains(kanban.getOrderNo()) || badOrderIds.contains(kanban.getOrderId()))
                .toList();
        var badKanbanNos = badRepackKanbans.stream()
                .map(RepackKanban::getKanbanNo)
                .collect(java.util.stream.Collectors.toSet());
        var badTargetKanbanNos = badRepackKanbans.stream()
                .map(RepackKanban::getTargetKanbanNo)
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.toSet());

        var generatedStockKanbans = kanbanRepo.findAll().stream()
                .filter(kanban -> badOrderNos.contains(kanban.getOrderNo())
                        || badKanbanNos.contains(kanban.getSourceKanbanNo())
                        || badTargetKanbanNos.contains(kanban.getKanbanNo()))
                .toList();
        var generatedKanbanNos = generatedStockKanbans.stream()
                .map(Kanban::getKanbanNo)
                .collect(java.util.stream.Collectors.toSet());

        var relatedNos = new java.util.HashSet<String>();
        relatedNos.addAll(badKanbanNos);
        relatedNos.addAll(badTargetKanbanNos);
        relatedNos.addAll(generatedKanbanNos);
        var badRecords = repackRecordRepo.findAll().stream()
                .filter(record -> relatedNos.contains(record.getSourceKanbanNo())
                        || relatedNos.contains(record.getTargetKanbanNo()))
                .toList();

        repackRecordRepo.deleteAll(badRecords);
        kanbanRepo.deleteAll(generatedStockKanbans);
        repackKanbanRepo.deleteAll(badRepackKanbans);
        repackOrderRepo.deleteAll(badOrders);
    }

    private boolean isCorrupted(String value) {
        return value == null || value.isBlank() || value.contains("?");
    }
}
