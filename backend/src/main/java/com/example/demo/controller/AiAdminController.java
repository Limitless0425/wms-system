package com.example.demo.controller;

import com.example.demo.entity.InventoryRecord;
import com.example.demo.entity.Kanban;
import com.example.demo.entity.OutboundKanban;
import com.example.demo.entity.Part;
import com.example.demo.entity.RepackKanban;
import com.example.demo.entity.RepackOrder;
import com.example.demo.entity.Warehouse;
import com.example.demo.repository.ContainerRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.InventoryRecordRepository;
import com.example.demo.repository.KanbanRepository;
import com.example.demo.repository.LocationRepository;
import com.example.demo.repository.OutboundKanbanRepository;
import com.example.demo.repository.PartRepository;
import com.example.demo.repository.RepackKanbanRepository;
import com.example.demo.repository.RepackOrderRepository;
import com.example.demo.repository.SupplierRepository;
import com.example.demo.repository.WarehouseRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/ai-admin")
public class AiAdminController {
    private static final Pattern PART_CODE_PATTERN = Pattern.compile("(?i)\\bPT[0-9A-Za-z_-]+\\b");
    private static final Pattern KANBAN_PATTERN = Pattern.compile("(?i)\\b(?:RKB|CKB|KB)[0-9A-Za-z_-]+\\b");
    private static final List<String> ACTIVE_INBOUND_STATUSES = List.of("SCANNED");
    private static final List<String> ACTIVE_REPACK_STATUSES = List.of("REPACK_INBOUND");

    private final PartRepository partRepo;
    private final KanbanRepository kanbanRepo;
    private final OutboundKanbanRepository outboundKanbanRepo;
    private final RepackKanbanRepository repackKanbanRepo;
    private final InventoryRecordRepository inventoryRecordRepo;
    private final WarehouseRepository warehouseRepo;
    private final LocationRepository locationRepo;
    private final SupplierRepository supplierRepo;
    private final CustomerRepository customerRepo;
    private final ContainerRepository containerRepo;
    private final RepackOrderRepository repackOrderRepo;

    private volatile Map<String, Object> cachedSummary = new LinkedHashMap<>();
    private volatile List<Map<String, Object>> cachedAnomalies = new ArrayList<>();
    private volatile LocalDateTime lastCheckTime;

    public AiAdminController(
            PartRepository partRepo,
            KanbanRepository kanbanRepo,
            OutboundKanbanRepository outboundKanbanRepo,
            RepackKanbanRepository repackKanbanRepo,
            InventoryRecordRepository inventoryRecordRepo,
            WarehouseRepository warehouseRepo,
            LocationRepository locationRepo,
            SupplierRepository supplierRepo,
            CustomerRepository customerRepo,
            ContainerRepository containerRepo,
            RepackOrderRepository repackOrderRepo) {
        this.partRepo = partRepo;
        this.kanbanRepo = kanbanRepo;
        this.outboundKanbanRepo = outboundKanbanRepo;
        this.repackKanbanRepo = repackKanbanRepo;
        this.inventoryRecordRepo = inventoryRecordRepo;
        this.warehouseRepo = warehouseRepo;
        this.locationRepo = locationRepo;
        this.supplierRepo = supplierRepo;
        this.customerRepo = customerRepo;
        this.containerRepo = containerRepo;
        this.repackOrderRepo = repackOrderRepo;
    }

    @PostConstruct
    public void init() {
        refreshMonitor();
    }

    @Scheduled(fixedDelay = 300000, initialDelay = 30000)
    public void scheduledCheck() {
        refreshMonitor();
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        refreshMonitor();
        return ok(cachedSummary);
    }

    @GetMapping("/anomalies")
    public Map<String, Object> anomalies() {
        refreshMonitor();
        return ok(cachedAnomalies);
    }

    @PostMapping("/ask")
    public Map<String, Object> ask(@RequestBody(required = false) Map<String, Object> body) {
        String question = body == null ? "" : Objects.toString(body.get("question"), "").trim();
        if (question.isBlank()) {
            return ok(answer("请输入要查询的问题，例如“总库存情况”“PT005库存”“看板KBxxx追溯”“异常数据”。", "help", List.of()));
        }
        refreshMonitor();

        String kanbanNo = extract(KANBAN_PATTERN, question);
        if (kanbanNo != null) {
            return ok(answerKanbanTrace(kanbanNo));
        }

        String partCode = extract(PART_CODE_PATTERN, question);
        Part part = partCode == null ? findPartByText(question) : partRepo.findByCodeIgnoreCase(partCode).orElse(null);
        if (part != null) {
            return ok(answerPartStock(part));
        }

        String lower = question.toLowerCase(Locale.ROOT);
        if (question.contains("异常") || question.contains("检查") || question.contains("错误")) {
            return ok(answer("当前定时检查发现 " + cachedAnomalies.size() + " 条需要关注的数据。", "anomaly", cachedAnomalies));
        }
        if (question.contains("低储") || question.contains("低库存")) {
            return ok(answerStockList("低储零件如下，建议优先补货或确认安全库存设置。", "low-stock", "LOW"));
        }
        if (question.contains("高储") || question.contains("高库存")) {
            return ok(answerStockList("高储零件如下，建议确认是否积压或高储阈值设置过低。", "high-stock", "HIGH"));
        }
        if (question.contains("缺货") || question.contains("无库存")) {
            return ok(answerStockList("当前缺货零件如下。", "out-of-stock", "OUT"));
        }
        if (question.contains("库存") || question.contains("总览") || question.contains("报表") || lower.contains("stock")) {
            return ok(answerTotalStock());
        }
        if (question.contains("能做什么") || question.contains("帮助") || question.contains("功能")) {
            return ok(answer(capabilityText(), "help", operationShortcuts()));
        }
        return ok(answer("我可以查询总库存、某零件库存、高低储/缺货、看板追溯和异常数据。也可以作为全权限账号进入各业务页面执行入库、出库、转包和基础资料维护。", "help", operationShortcuts()));
    }

    private synchronized void refreshMonitor() {
        Map<String, StockLine> stock = currentStock();
        List<Part> parts = partRepo.findAll();
        List<Map<String, Object>> stockLines = parts.stream()
                .map(part -> stockLineMap(part, stock.getOrDefault(part.getCode(), new StockLine(part.getCode(), part.getName()))))
                .sorted(Comparator
                        .comparingInt((Map<String, Object> row) -> alertRank(Objects.toString(row.get("alert"), "")))
                        .thenComparing(row -> Objects.toString(row.get("partCode"), "")))
                .toList();

        long totalQty = stockLines.stream().mapToLong(row -> number(row.get("qty"))).sum();
        long lowCount = stockLines.stream().filter(row -> "LOW".equals(row.get("alert"))).count();
        long highCount = stockLines.stream().filter(row -> "HIGH".equals(row.get("alert"))).count();
        long outCount = stockLines.stream().filter(row -> "OUT".equals(row.get("alert"))).count();

        List<Map<String, Object>> anomalies = detectAnomalies(stockLines);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("lastCheckTime", LocalDateTime.now());
        summary.put("partCount", parts.size());
        summary.put("supplierCount", supplierRepo.count());
        summary.put("customerCount", customerRepo.count());
        summary.put("warehouseCount", warehouseRepo.count());
        summary.put("locationCount", locationRepo.count());
        summary.put("containerCount", containerRepo.count());
        summary.put("totalQty", totalQty);
        summary.put("lowStockCount", lowCount);
        summary.put("highStockCount", highCount);
        summary.put("outOfStockCount", outCount);
        summary.put("anomalyCount", anomalies.size());
        summary.put("topWarnings", anomalies.stream().limit(8).toList());
        summary.put("stockLines", stockLines.stream().limit(20).toList());
        summary.put("actions", operationShortcuts());

        this.cachedSummary = summary;
        this.cachedAnomalies = anomalies;
        this.lastCheckTime = (LocalDateTime) summary.get("lastCheckTime");
    }

    private Map<String, StockLine> currentStock() {
        Map<String, StockLine> stock = new LinkedHashMap<>();
        for (Kanban kanban : kanbanRepo.findAll()) {
            if (kanban.getPartCode() == null || !ACTIVE_INBOUND_STATUSES.contains(kanban.getStatus())) continue;
            if (Boolean.TRUE.equals(kanban.getSealed())) continue;
            addStock(stock, kanban.getPartCode(), kanban.getPartName(), kanban.getQty(), kanban.getWarehouseName(), kanban.getLocationName(), kanban.getKanbanNo());
        }
        for (RepackKanban kanban : repackKanbanRepo.findAll()) {
            if (kanban.getPartCode() == null || !ACTIVE_REPACK_STATUSES.contains(kanban.getStatus())) continue;
            addStock(stock, kanban.getPartCode(), kanban.getPartName(), kanban.getQty(), kanban.getWarehouseName(), kanban.getLocationName(), kanban.getKanbanNo());
        }
        return stock;
    }

    private void addStock(Map<String, StockLine> stock, String partCode, String partName, Integer qty, String warehouse, String location, String kanbanNo) {
        StockLine line = stock.computeIfAbsent(partCode, key -> new StockLine(partCode, partName));
        line.partName = firstText(line.partName, partName);
        line.qty += qty == null ? 0 : qty;
        if (warehouse != null && !warehouse.isBlank()) line.warehouseQty.merge(warehouse, qty == null ? 0 : qty, Integer::sum);
        if (location != null && !location.isBlank()) line.locationQty.merge(location, qty == null ? 0 : qty, Integer::sum);
        if (kanbanNo != null && line.sampleKanbans.size() < 5) line.sampleKanbans.add(kanbanNo);
    }

    private List<Map<String, Object>> detectAnomalies(List<Map<String, Object>> stockLines) {
        List<Map<String, Object>> anomalies = new ArrayList<>();
        for (Map<String, Object> row : stockLines) {
            String alert = Objects.toString(row.get("alert"), "");
            if ("OUT".equals(alert)) anomalies.add(issue("缺货", "HIGH", row.get("partCode"), row.get("partName"), "当前库存为 0"));
            if ("LOW".equals(alert)) anomalies.add(issue("低储", "MEDIUM", row.get("partCode"), row.get("partName"), "当前库存低于低储值"));
            if ("HIGH".equals(alert)) anomalies.add(issue("高储", "LOW", row.get("partCode"), row.get("partName"), "当前库存高于高储值"));
        }
        for (Warehouse warehouse : warehouseRepo.findAll()) {
            int used = warehouseUsedQty(warehouse.getName());
            int capacity = warehouse.getCapacity() == null ? 0 : warehouse.getCapacity();
            if (capacity > 0 && used > capacity) {
                anomalies.add(issue("仓库超容", "HIGH", warehouse.getCode(), warehouse.getName(), "当前库存 " + used + "，仓库总容量 " + capacity));
            }
        }
        for (Part part : partRepo.findAll()) {
            if (part.getOriginalPackageQty() == null || part.getOriginalPackageQty() <= 0
                    || part.getTargetPackageQty() == null || part.getTargetPackageQty() <= 0
                    || part.getRepackContainerType() == null || part.getRepackContainerType().isBlank()) {
                anomalies.add(issue("零件转包资料不完整", "MEDIUM", part.getCode(), part.getName(), "请维护原包装容量、转包容量和转包器具类型"));
            }
        }
        for (RepackOrder order : repackOrderRepo.findAll()) {
            List<RepackKanban> kanbans = repackKanbanRepo.findByOrderNo(order.getOrderNo());
            boolean hasPending = kanbans.stream().anyMatch(k -> "PRINTED".equals(k.getStatus()));
            if ("COMPLETED".equals(order.getStatus()) && hasPending) {
                anomalies.add(issue("转包状态不一致", "HIGH", order.getOrderNo(), order.getSupplierName(), "转包单已完成但仍有待扫描看板"));
            }
            if (!"COMPLETED".equals(order.getStatus()) && !kanbans.isEmpty() && !hasPending) {
                anomalies.add(issue("转包状态不一致", "MEDIUM", order.getOrderNo(), order.getSupplierName(), "转包看板已处理完但转包单未完成"));
            }
        }
        return anomalies.stream()
                .sorted(Comparator.comparingInt(row -> severityRank(Objects.toString(row.get("severity"), ""))))
                .limit(80)
                .toList();
    }

    private int warehouseUsedQty(String warehouseName) {
        if (warehouseName == null || warehouseName.isBlank()) return 0;
        int inbound = kanbanRepo.findAll().stream()
                .filter(k -> warehouseName.equals(k.getWarehouseName()))
                .filter(k -> ACTIVE_INBOUND_STATUSES.contains(k.getStatus()))
                .filter(k -> !Boolean.TRUE.equals(k.getSealed()))
                .mapToInt(k -> k.getQty() == null ? 0 : k.getQty())
                .sum();
        int repack = repackKanbanRepo.findAll().stream()
                .filter(k -> warehouseName.equals(k.getWarehouseName()))
                .filter(k -> ACTIVE_REPACK_STATUSES.contains(k.getStatus()))
                .mapToInt(k -> k.getQty() == null ? 0 : k.getQty())
                .sum();
        return inbound + repack;
    }

    private Map<String, Object> answerTotalStock() {
        long totalQty = number(cachedSummary.get("totalQty"));
        String text = "当前总库存 " + totalQty + "，零件 " + cachedSummary.get("partCount")
                + " 种；缺货 " + cachedSummary.get("outOfStockCount")
                + " 种，低储 " + cachedSummary.get("lowStockCount")
                + " 种，高储 " + cachedSummary.get("highStockCount")
                + " 种。最近一次检查时间：" + lastCheckTime + "。";
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) cachedSummary.getOrDefault("stockLines", List.of());
        return answer(text, "stock-summary", lines);
    }

    private Map<String, Object> answerStockList(String text, String type, String alert) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lines = (List<Map<String, Object>>) cachedSummary.getOrDefault("stockLines", List.of());
        List<Map<String, Object>> allLines = partRepo.findAll().stream()
                .map(part -> stockLineMap(part, currentStock().getOrDefault(part.getCode(), new StockLine(part.getCode(), part.getName()))))
                .filter(row -> alert.equals(row.get("alert")))
                .sorted(Comparator.comparing(row -> Objects.toString(row.get("partCode"), "")))
                .toList();
        return answer(text + " 共 " + allLines.size() + " 条。", type, allLines.isEmpty() ? lines.stream().filter(row -> alert.equals(row.get("alert"))).toList() : allLines);
    }

    private Map<String, Object> answerPartStock(Part part) {
        StockLine line = currentStock().getOrDefault(part.getCode(), new StockLine(part.getCode(), part.getName()));
        Map<String, Object> row = stockLineMap(part, line);
        List<InventoryRecord> records = inventoryRecordRepo.findByPartCode(part.getCode()).stream()
                .sorted(Comparator.comparing(InventoryRecord::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .toList();
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(row);
        records.forEach(record -> items.add(recordMap(record)));
        String alertText = alertText(Objects.toString(row.get("alert"), ""));
        String text = part.getCode() + " " + part.getName() + " 当前库存 " + row.get("qty")
                + "，低储 " + row.get("lowStock") + "，高储 " + row.get("highStock")
                + "，状态：" + alertText + "。";
        return answer(text, "part-stock", items);
    }

    private Map<String, Object> answerKanbanTrace(String kanbanNo) {
        List<Map<String, Object>> items = new ArrayList<>();
        kanbanRepo.findByKanbanNo(kanbanNo).ifPresent(k -> {
            items.add(kanbanMap("入库看板", k.getKanbanNo(), k.getOrderNo(), k.getPartCode(), k.getPartName(), k.getQty(), k.getStatus(), k.getWarehouseName(), k.getLocationName(), k.getSourceKanbanNo()));
            outboundKanbanRepo.findAll().stream()
                    .filter(out -> kanbanNo.equals(out.getSourceKanbanNo()))
                    .forEach(out -> items.add(outboundMap(out)));
            repackKanbanRepo.findAll().stream()
                    .filter(r -> kanbanNo.equals(r.getSourceKanbanNo()) || kanbanNo.equals(r.getTargetKanbanNo()))
                    .forEach(r -> items.add(repackMap(r)));
        });
        outboundKanbanRepo.findByKanbanNo(kanbanNo).ifPresent(out -> {
            items.add(outboundMap(out));
            if (out.getSourceKanbanNo() != null) {
                kanbanRepo.findByKanbanNo(out.getSourceKanbanNo()).ifPresent(k -> items.add(kanbanMap("来源入库看板", k.getKanbanNo(), k.getOrderNo(), k.getPartCode(), k.getPartName(), k.getQty(), k.getStatus(), k.getWarehouseName(), k.getLocationName(), k.getSourceKanbanNo())));
            }
        });
        repackKanbanRepo.findByKanbanNo(kanbanNo).ifPresent(r -> {
            items.add(repackMap(r));
            if (r.getSourceKanbanNo() != null) {
                kanbanRepo.findByKanbanNo(r.getSourceKanbanNo()).ifPresent(k -> items.add(kanbanMap("来源入库看板", k.getKanbanNo(), k.getOrderNo(), k.getPartCode(), k.getPartName(), k.getQty(), k.getStatus(), k.getWarehouseName(), k.getLocationName(), k.getSourceKanbanNo())));
                outboundKanbanRepo.findByKanbanNo(r.getSourceKanbanNo()).ifPresent(out -> items.add(outboundMap(out)));
                repackKanbanRepo.findByKanbanNo(r.getSourceKanbanNo()).ifPresent(src -> items.add(repackMap(src)));
            }
        });
        inventoryRecordRepo.findByKanbanNo(kanbanNo).stream()
                .sorted(Comparator.comparing(InventoryRecord::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(record -> items.add(recordMap(record)));
        String text = items.isEmpty()
                ? "没有查到看板 " + kanbanNo + " 的相关记录，请确认看板号是否完整。"
                : "已查到看板 " + kanbanNo + " 的相关看板和库存流水，共 " + items.size() + " 条。";
        return answer(text, "kanban-trace", items);
    }

    private Part findPartByText(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        return partRepo.findAll().stream()
                .filter(part -> contains(normalized, part.getCode()) || contains(normalized, part.getName()))
                .findFirst()
                .orElse(null);
    }

    private boolean contains(String source, String value) {
        return value != null && !value.isBlank() && source.contains(value.toLowerCase(Locale.ROOT));
    }

    private String extract(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group().toUpperCase(Locale.ROOT) : null;
    }

    private Map<String, Object> stockLineMap(Part part, StockLine line) {
        int qty = line.qty;
        int low = part.getLowStock() == null ? 0 : part.getLowStock();
        int high = part.getHighStock() == null ? 0 : part.getHighStock();
        String alert = qty <= 0 ? "OUT" : (low > 0 && qty < low ? "LOW" : (high > 0 && qty > high ? "HIGH" : "NORMAL"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("kind", "库存");
        row.put("partCode", part.getCode());
        row.put("partName", firstText(part.getName(), line.partName));
        row.put("supplierName", part.getSupplierName());
        row.put("qty", qty);
        row.put("unit", part.getUnit());
        row.put("lowStock", low);
        row.put("highStock", high);
        row.put("alert", alert);
        row.put("alertText", alertText(alert));
        row.put("warehouses", line.warehouseQty);
        row.put("locations", line.locationQty);
        row.put("sampleKanbans", line.sampleKanbans);
        return row;
    }

    private Map<String, Object> answer(String text, String type, List<Map<String, Object>> items) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("answer", text);
        data.put("type", type);
        data.put("items", items);
        data.put("summary", cachedSummary);
        data.put("time", LocalDateTime.now());
        return data;
    }

    private Map<String, Object> ok(Object data) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("code", 200);
        res.put("data", data);
        return res;
    }

    private Map<String, Object> issue(String type, String severity, Object code, Object name, String message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", type);
        row.put("severity", severity);
        row.put("code", code);
        row.put("name", name);
        row.put("message", message);
        return row;
    }

    private Map<String, Object> kanbanMap(String kind, String kanbanNo, String orderNo, String partCode, String partName, Integer qty, String status, String warehouse, String location, String sourceKanbanNo) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("kind", kind);
        row.put("kanbanNo", kanbanNo);
        row.put("orderNo", orderNo);
        row.put("partCode", partCode);
        row.put("partName", partName);
        row.put("qty", qty);
        row.put("status", statusText(status));
        row.put("warehouseName", warehouse);
        row.put("locationName", location);
        row.put("sourceKanbanNo", sourceKanbanNo);
        return row;
    }

    private Map<String, Object> outboundMap(OutboundKanban out) {
        return kanbanMap("出库看板", out.getKanbanNo(), out.getOrderNo(), out.getPartCode(), out.getPartName(), out.getActualQty(), out.getStatus(), out.getWarehouseName(), out.getLocationName(), out.getSourceKanbanNo());
    }

    private Map<String, Object> repackMap(RepackKanban repack) {
        return kanbanMap("转包看板", repack.getKanbanNo(), repack.getOrderNo(), repack.getPartCode(), repack.getPartName(), repack.getQty(), repack.getStatus(), repack.getWarehouseName(), repack.getLocationName(), repack.getSourceKanbanNo());
    }

    private Map<String, Object> recordMap(InventoryRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("kind", "库存流水");
        row.put("kanbanNo", record.getKanbanNo());
        row.put("orderNo", record.getRefOrderNo());
        row.put("partCode", record.getPartCode());
        row.put("partName", record.getPartName());
        row.put("qty", record.getQty());
        row.put("type", recordTypeText(record.getType()));
        row.put("locationName", record.getLocationName());
        row.put("time", record.getCreateTime());
        return row;
    }

    private List<Map<String, Object>> operationShortcuts() {
        return List.of(
                shortcut("新建入库单", "/inbound/order"),
                shortcut("扫码入库", "/inbound/scan"),
                shortcut("新建出库单", "/outbound/order"),
                shortcut("带单出库", "/outbound/scan"),
                shortcut("不带单出库", "/outbound/direct-scan"),
                shortcut("新建/执行转包单", "/operations/repack"),
                shortcut("转包作业", "/operations/repack-scan"),
                shortcut("看板管理", "/kanban/manage"),
                shortcut("库存监控", "/inventory/stock"),
                shortcut("总库存报表", "/inventory/report"),
                shortcut("库存追溯", "/inventory/trace"),
                shortcut("基础信息维护", "/baseinfo/part")
        );
    }

    private Map<String, Object> shortcut(String title, String path) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", title);
        row.put("path", path);
        return row;
    }

    private String capabilityText() {
        return "AI仓库管理员拥有全权限账号，可进入系统完成入库、出库、转包、看板、基础资料和库存管理。当前页面支持快速问答：总库存、某零件库存、高低储、缺货、看板追溯、库存流水和异常数据检查。";
    }

    private int alertRank(String alert) {
        return switch (alert) {
            case "OUT" -> 0;
            case "LOW" -> 1;
            case "HIGH" -> 2;
            default -> 3;
        };
    }

    private int severityRank(String severity) {
        return switch (severity) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            default -> 2;
        };
    }

    private long number(Object value) {
        if (value instanceof Number n) return n.longValue();
        return 0;
    }

    private String firstText(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String alertText(String alert) {
        return switch (alert) {
            case "OUT" -> "缺货";
            case "LOW" -> "低储";
            case "HIGH" -> "高储";
            default -> "正常";
        };
    }

    private String statusText(String status) {
        if (status == null) return "";
        return switch (status) {
            case "PRINTED" -> "待扫描";
            case "SCANNED" -> "已入库";
            case "SEALED" -> "封存";
            case "REPACKED" -> "已转包";
            case "REPACK_INBOUND" -> "转包入库";
            case "OUTBOUND" -> "已出库";
            case "PENDING_OUTBOUND" -> "待出库";
            case "CANCELLED" -> "已作废";
            case "COMPLETED" -> "已完成";
            case "PROCESSING", "PARTIAL" -> "处理中";
            default -> status;
        };
    }

    private String recordTypeText(String type) {
        if (type == null) return "";
        return switch (type) {
            case "IN" -> "入库";
            case "OUT" -> "出库";
            case "REPACK_OUT" -> "转包出库";
            case "REPACK_IN" -> "转包入库";
            case "RETURN" -> "退库";
            default -> type;
        };
    }

    private static class StockLine {
        private final String partCode;
        private String partName;
        private int qty;
        private final Map<String, Integer> warehouseQty = new LinkedHashMap<>();
        private final Map<String, Integer> locationQty = new LinkedHashMap<>();
        private final List<String> sampleKanbans = new ArrayList<>();

        private StockLine(String partCode, String partName) {
            this.partCode = partCode;
            this.partName = partName;
        }
    }
}
