package com.example.demo.controller;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryRecordRepository inventoryRepo;
    private final LocationRepository locationRepo;
    private final WarehouseRepository warehouseRepo;
    private final PartRepository partRepo;
    private final RepackKanbanRepository repackKanbanRepo;
    private final KanbanRepository kanbanRepo;
    private final OutboundKanbanRepository outboundKanbanRepo;
    private final RepackOrderRepository repackOrderRepo;

    public InventoryController(InventoryRecordRepository inventoryRepo, LocationRepository locationRepo,
                               WarehouseRepository warehouseRepo, PartRepository partRepo,
                               RepackKanbanRepository repackKanbanRepo,
                               KanbanRepository kanbanRepo,
                               OutboundKanbanRepository outboundKanbanRepo,
                               RepackOrderRepository repackOrderRepo) {
        this.inventoryRepo = inventoryRepo;
        this.locationRepo = locationRepo;
        this.warehouseRepo = warehouseRepo;
        this.partRepo = partRepo;
        this.repackKanbanRepo = repackKanbanRepo;
        this.kanbanRepo = kanbanRepo;
        this.outboundKanbanRepo = outboundKanbanRepo;
        this.repackOrderRepo = repackOrderRepo;
    }

    @GetMapping("/trace/{partCode}")
    public Map<String, Object> traceByPart(@PathVariable String partCode) {
        var list = inventoryRepo.findByPartCode(partCode);
        int totalIn = list.stream().filter(this::isInbound).mapToInt(record -> Math.abs(safe(record.getQty()))).sum();
        int totalOut = list.stream().filter(this::isOutbound).mapToInt(record -> Math.abs(safe(record.getQty()))).sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("partCode", partCode);
        result.put("totalInbound", totalIn);
        result.put("totalOutbound", totalOut);
        result.put("currentStock", totalIn - totalOut);
        result.put("records", list);
        return Map.of("code", 200, "data", result);
    }

    @GetMapping("/kanban-trace/{kanbanNo}")
    public Map<String, Object> traceByKanban(@PathVariable String kanbanNo) {
        var repackKanban = repackKanbanRepo.findByKanbanNo(kanbanNo).orElse(null);
        if (repackKanban == null) return Map.of("code", 200, "data", traceNormalKanban(kanbanNo));

        Map<Long, Map<String, Object>> rows = new LinkedHashMap<>();
        putRow(rows, repackKanbanRow(repackKanban));
        if (isHandRepackKanban(repackKanban) && repackKanban.getSourceKanbanNo() != null) {
            for (String sourceNo : splitKanbanNos(repackKanban.getSourceKanbanNo())) {
                kanbanRepo.findByKanbanNo(sourceNo).ifPresent(kanban -> putRow(rows, stockKanbanRow(kanban)));
                outboundKanbanRepo.findByKanbanNo(sourceNo).ifPresent(kanban -> putRow(rows, outboundKanbanTraceRow(kanban)));
                repackKanbanRepo.findByKanbanNo(sourceNo).ifPresent(kanban -> putRow(rows, repackKanbanRow(kanban)));
            }
        }
        if (isHandRepackKanban(repackKanban) && repackKanban.getTargetKanbanNo() != null) {
            repackKanbanRepo.findByKanbanNo(repackKanban.getTargetKanbanNo()).ifPresent(kanban -> putRow(rows, repackKanbanRow(kanban)));
            kanbanRepo.findByKanbanNo(repackKanban.getTargetKanbanNo()).ifPresent(kanban -> putRow(rows, stockKanbanRow(kanban)));
            outboundKanbanRepo.findByKanbanNo(repackKanban.getTargetKanbanNo()).ifPresent(kanban -> putRow(rows, outboundKanbanTraceRow(kanban)));
        }
        return Map.of("code", 200, "data", sortKanbanRows(rows));
    }

    private List<Map<String, Object>> traceNormalKanban(String kanbanNo) {
        Map<Long, Map<String, Object>> rows = new LinkedHashMap<>();
        Kanban sourceKanban = kanbanRepo.findByKanbanNo(kanbanNo).orElse(null);
        if (sourceKanban != null) putRow(rows, stockKanbanRow(sourceKanban));
        outboundKanbanRepo.findByKanbanNo(kanbanNo).ifPresent(outbound -> {
            putRow(rows, outboundKanbanTraceRow(outbound));
            if (!isDirectOutboundKanban(outbound)) return;
            for (String sourceNo : splitKanbanNos(outbound.getSourceKanbanNo())) {
                kanbanRepo.findByKanbanNo(sourceNo).ifPresent(kanban -> putRow(rows, stockKanbanRow(kanban)));
                repackKanbanRepo.findByKanbanNo(sourceNo).ifPresent(kanban -> putRow(rows, repackKanbanRow(kanban)));
            }
        });

        for (var outbound : outboundKanbanRepo.findAll()) {
            if (!containsKanban(outbound.getSourceKanbanNo(), kanbanNo)) continue;
            if (!isDirectOutboundKanban(outbound)) continue;
            putRow(rows, outboundKanbanTraceRow(outbound));
        }

        for (var repack : repackKanbanRepo.findAll()) {
            if (!containsKanban(repack.getSourceKanbanNo(), kanbanNo) && !Objects.equals(repack.getTargetKanbanNo(), kanbanNo)) continue;
            if (!isHandRepackKanban(repack)) continue;
            putRow(rows, repackKanbanRow(repack));
        }

        return sortKanbanRows(rows);
    }

    @GetMapping("/warehouse-summary")
    public Map<String, Object> warehouseSummary() {
        List<Location> locations = locationRepo.findAll();
        Map<String, Location> locationLookup = locationLookup(locations);
        Map<Long, Integer> warehouseStock = new HashMap<>();
        for (var record : inventoryRepo.findAll()) {
            Location location = findLocation(locationLookup, record.getLocationName());
            if (location != null) {
                warehouseStock.merge(location.getWarehouseId(), signedQty(record), Integer::sum);
            }
        }

        Map<Long, Long> locationCounts = locations.stream()
                .collect(Collectors.groupingBy(Location::getWarehouseId, Collectors.counting()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Warehouse warehouse : warehouseRepo.findAll()) {
            int stock = Math.max(warehouseStock.getOrDefault(warehouse.getId(), 0), 0);
            int capacity = safe(warehouse.getCapacity());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("warehouseId", warehouse.getId());
            item.put("warehouseCode", warehouse.getCode());
            item.put("warehouseName", warehouse.getName());
            item.put("area", warehouse.getArea());
            item.put("locationCount", locationCounts.getOrDefault(warehouse.getId(), 0L));
            item.put("stockQty", stock);
            item.put("capacity", capacity);
            item.put("remainingCapacity", capacity - stock);
            item.put("usageRate", capacity <= 0 ? 0 : Math.round(stock * 1000.0 / capacity) / 10.0);
            result.add(item);
        }
        result.sort(Comparator.comparing(item -> String.valueOf(item.get("warehouseCode"))));
        return Map.of("code", 200, "data", result);
    }

    @GetMapping("/total-stock-report")
    public Map<String, Object> totalStockReport() {
        Map<Long, Part> parts = partRepo.findAll().stream()
                .collect(Collectors.toMap(Part::getId, Function.identity()));
        Map<Long, Integer> stock = activeStockByPart();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Part part : parts.values()) {
            Map<String, Object> item = stockItem(Math.max(stock.getOrDefault(part.getId(), 0), 0), part);
            item.put("partId", part.getId());
            item.put("supplierId", part.getSupplierId());
            item.put("supplierName", part.getSupplierName());
            result.add(item);
        }
        result.sort(Comparator
                .comparingInt((Map<String, Object> item) -> alertRank(String.valueOf(item.get("alert"))))
                .thenComparing(item -> String.valueOf(item.get("partCode"))));
        return Map.of("code", 200, "data", result);
    }

    @GetMapping("/stock-summary")
    public Map<String, Object> stockSummary(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long locationId,
            @RequestParam(defaultValue = "") String partCode,
            @RequestParam(required = false) Long supplierId) {
        Map<Long, Part> parts = partRepo.findAll().stream()
                .collect(Collectors.toMap(Part::getId, Function.identity()));
        List<Location> locations = locationRepo.findAll();
        Map<Long, Location> locationsById = locations.stream()
                .collect(Collectors.toMap(Location::getId, Function.identity()));
        Map<String, Location> locationLookup = locationLookup(locations);
        Map<Long, Warehouse> warehouses = warehouseRepo.findAll().stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity()));

        Map<String, Integer> stock = new LinkedHashMap<>();
        for (var record : inventoryRepo.findAll()) {
            Part part = parts.get(record.getPartId());
            Location location = findLocation(locationLookup, record.getLocationName());
            if (part == null || location == null) continue;
            if (warehouseId != null && !warehouseId.equals(location.getWarehouseId())) continue;
            if (locationId != null && !locationId.equals(location.getId())) continue;
            if (!partCode.isBlank() && !contains(part.getCode(), partCode)) continue;
            if (supplierId != null && !supplierId.equals(part.getSupplierId())) continue;
            String key = part.getId() + "|" + location.getId();
            stock.merge(key, signedQty(record), Integer::sum);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        stock.forEach((key, qty) -> {
            String[] ids = key.split("\\|");
            Part part = parts.get(Long.parseLong(ids[0]));
            Location location = locationsById.get(Long.parseLong(ids[1]));
            Warehouse warehouse = warehouses.get(location.getWarehouseId());
            Map<String, Object> item = stockItem(qty, part);
            item.put("warehouseId", location.getWarehouseId());
            item.put("warehouseCode", warehouse == null ? "" : warehouse.getCode());
            item.put("warehouseName", location.getWarehouseName());
            item.put("locationId", location.getId());
            item.put("locationCode", location.getCode());
            item.put("locationName", location.getName());
            item.put("locationCapacity", safe(location.getCapacity()));
            item.put("supplierId", part.getSupplierId());
            item.put("supplierName", part.getSupplierName());
            result.add(item);
        });
        result.sort(Comparator
                .comparing((Map<String, Object> item) -> String.valueOf(item.get("warehouseCode")))
                .thenComparing(item -> String.valueOf(item.get("locationCode")))
                .thenComparing(item -> String.valueOf(item.get("partCode"))));
        return Map.of("code", 200, "data", result);
    }

    private Map<String, Object> repackKanbanRow(RepackKanban kanban) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", -safeLong(kanban.getId()));
        row.put("partCode", kanban.getPartCode());
        row.put("partName", kanban.getPartName());
        row.put("type", "REPACK_KANBAN");
        row.put("typeLabel", "转包看板");
        row.put("qty", kanban.getQty());
        row.put("locationName", kanban.getLocationName());
        row.put("refOrderNo", kanban.getOrderNo());
        row.put("kanbanNo", kanban.getKanbanNo());
        row.put("sourceKanbanNo", kanban.getSourceKanbanNo());
        row.put("targetKanbanNo", kanban.getTargetKanbanNo());
        row.put("createTime", kanban.getPrintTime());
        return row;
    }

    private Map<String, Object> stockKanbanRow(Kanban kanban) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", -2_000_000L - safeLong(kanban.getId()));
        row.put("partCode", kanban.getPartCode());
        row.put("partName", kanban.getPartName());
        row.put("type", "STOCK_KANBAN");
        row.put("typeLabel", "库存看板");
        row.put("status", kanban.getStatus());
        row.put("qty", kanban.getQty());
        row.put("locationName", kanban.getLocationName());
        row.put("refOrderNo", kanban.getOrderNo());
        row.put("kanbanNo", kanban.getKanbanNo());
        row.put("createTime", kanban.getOutboundTime() != null ? kanban.getOutboundTime() : kanban.getScanTime());
        return row;
    }

    private Map<String, Object> outboundKanbanTraceRow(OutboundKanban kanban) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", -1_000_000L - safeLong(kanban.getId()));
        row.put("partCode", kanban.getPartCode());
        row.put("partName", kanban.getPartName());
        row.put("type", "OUTBOUND_KANBAN");
        row.put("typeLabel", "出库看板");
        row.put("status", kanban.getStatus());
        row.put("qty", kanban.getActualQty());
        row.put("locationName", kanban.getLocationName());
        row.put("refOrderNo", kanban.getOrderNo());
        row.put("kanbanNo", kanban.getKanbanNo());
        row.put("sourceKanbanNo", kanban.getSourceKanbanNo());
        row.put("createTime", kanban.getOutboundTime() != null ? kanban.getOutboundTime() : kanban.getPrintTime());
        return row;
    }

    private Map<String, Object> recordRow(InventoryRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", safeLong(record.getId()));
        row.put("partCode", record.getPartCode());
        row.put("partName", record.getPartName());
        row.put("type", record.getType());
        row.put("qty", record.getQty());
        row.put("locationName", record.getLocationName());
        row.put("refOrderNo", record.getRefOrderNo());
        row.put("kanbanNo", record.getKanbanNo());
        row.put("createTime", record.getCreateTime());
        return row;
    }

    private void putRow(Map<Long, Map<String, Object>> rows, Map<String, Object> row) {
        Object id = row.get("id");
        long key = id instanceof Number number ? number.longValue() : rows.size() + 1L;
        rows.putIfAbsent(key, row);
    }

    private Map<String, Object> stockItem(int qty, Part part) {
        int low = part.getLowStock() == null ? 50 : part.getLowStock();
        Integer high = part.getHighStock();
        String alert = qty <= 0 ? "缺货"
                : qty <= low ? "低储"
                : high != null && high > 0 && qty >= high ? "高储" : "正常";
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("partCode", part.getCode());
        item.put("partName", part.getName());
        item.put("unit", part.getUnit());
        item.put("qty", qty);
        item.put("lowStock", low);
        item.put("highStock", high);
        item.put("alert", alert);
        return item;
    }

    private Map<Long, Integer> activeStockByPart() {
        Map<Long, Integer> stock = new HashMap<>();
        for (var kanban : kanbanRepo.findAll()) {
            if (!"SCANNED".equals(kanban.getStatus())) continue;
            if (Boolean.TRUE.equals(kanban.getSealed())) continue;
            if (kanban.getPartId() == null) continue;
            int qty = safe(kanban.getQty());
            if (qty > 0) stock.merge(kanban.getPartId(), qty, Integer::sum);
        }
        for (var kanban : repackKanbanRepo.findAll()) {
            if (!"REPACK_INBOUND".equals(kanban.getStatus())) continue;
            if (kanban.getPartId() == null) continue;
            int qty = safe(kanban.getQty());
            if (qty > 0) stock.merge(kanban.getPartId(), qty, Integer::sum);
        }
        return stock;
    }

    private Map<String, Location> locationLookup(List<Location> locations) {
        Map<String, Location> lookup = new HashMap<>();
        for (Location location : locations) {
            if (location.getCode() != null) lookup.put(normalize(location.getCode()), location);
            if (location.getName() != null) lookup.put(normalize(location.getName()), location);
        }
        return lookup;
    }

    private Location findLocation(Map<String, Location> lookup, String locationName) {
        return locationName == null ? null : lookup.get(normalize(locationName));
    }

    private int signedQty(InventoryRecord record) {
        int qty = Math.abs(safe(record.getQty()));
        if (isInbound(record)) return qty;
        if (isOutbound(record)) return -qty;
        return safe(record.getQty());
    }

    private boolean isInbound(InventoryRecord record) {
        return "INBOUND".equals(record.getType()) || "MANUAL_INBOUND".equals(record.getType())
                || "REPACK_INBOUND".equals(record.getType())
                || "REPACK_IN".equals(record.getType())
                || "RETURN_INBOUND".equals(record.getType());
    }

    private boolean isOutbound(InventoryRecord record) {
        return "OUTBOUND".equals(record.getType()) || "REPACK_OUTBOUND".equals(record.getType())
                || "REPACK_OUT".equals(record.getType())
                || "OUTBOUND_DIRECT".equals(record.getType());
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT));
    }

    private boolean containsKanban(String values, String kanbanNo) {
        if (values == null || kanbanNo == null) return false;
        return Arrays.stream(values.split(","))
                .map(String::trim)
                .anyMatch(kanbanNo::equals);
    }

    private List<String> splitKanbanNos(String values) {
        if (values == null || values.isBlank()) return List.of();
        return Arrays.stream(values.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private List<Map<String, Object>> sortKanbanRows(Map<Long, Map<String, Object>> rows) {
        return rows.values().stream()
                .sorted(Comparator
                        .comparing((Map<String, Object> row) -> String.valueOf(row.get("createTime")), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(row -> String.valueOf(row.get("kanbanNo"))))
                .toList();
    }

    private boolean isDirectOutboundKanban(OutboundKanban kanban) {
        if (kanban == null) return false;
        return "不带单出库".equals(kanban.getOutboundType()) || text(kanban.getOrderNo()).startsWith("DIRECT");
    }

    private boolean isHandRepackKanban(RepackKanban kanban) {
        if (kanban == null || kanban.getOrderId() == null) return false;
        return repackOrderRepo.findById(kanban.getOrderId())
                .map(order -> !text(order.getSourceKanbanNo()).isBlank() && !text(order.getSourceBusinessType()).isBlank())
                .orElse(false);
    }

    private int alertRank(String alert) {
        return switch (alert) {
            case "缺货" -> 0;
            case "低储" -> 1;
            case "高储" -> 2;
            default -> 3;
        };
    }

    private String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT); }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private int safe(Integer value) { return value == null ? 0 : value; }
    private long safeLong(Long value) { return value == null ? 0L : value; }
}
