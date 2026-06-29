package com.example.demo.controller;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/outbound")
public class OutboundController {

    private static final Set<String> OUTBOUND_TYPES = Set.of("出库", "退货", "调账出库", "调账退货（无实物）");

    private final OutboundOrderRepository orderRepo;
    private final OutboundKanbanRepository outboundKanbanRepo;
    private final KanbanRepository kanbanRepo;
    private final InventoryRecordRepository inventoryRepo;
    private final CustomerRepository customerRepo;
    private final PartRepository partRepo;
    private final SupplierRepository supplierRepo;
    private final WarehouseRepository warehouseRepo;
    private final LocationRepository locationRepo;

    public OutboundController(OutboundOrderRepository orderRepo,
                              OutboundKanbanRepository outboundKanbanRepo,
                              KanbanRepository kanbanRepo,
                              InventoryRecordRepository inventoryRepo,
                              CustomerRepository customerRepo,
                              PartRepository partRepo,
                              SupplierRepository supplierRepo,
                              WarehouseRepository warehouseRepo,
                              LocationRepository locationRepo) {
        this.orderRepo = orderRepo;
        this.outboundKanbanRepo = outboundKanbanRepo;
        this.kanbanRepo = kanbanRepo;
        this.inventoryRepo = inventoryRepo;
        this.customerRepo = customerRepo;
        this.partRepo = partRepo;
        this.supplierRepo = supplierRepo;
        this.warehouseRepo = warehouseRepo;
        this.locationRepo = locationRepo;
    }

    @GetMapping("/orders")
    public Map<String, Object> listOrders(
            @RequestParam(defaultValue = "") String supplier,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String orderNo) {
        List<OutboundOrder> list = new ArrayList<>(orderRepo.findAll());
        if (!supplier.isBlank()) list.removeIf(o -> !contains(o.getSupplierName(), supplier));
        if (!status.isBlank()) list.removeIf(o -> !status.equals(o.getStatus()));
        if (!orderNo.isBlank()) list.removeIf(o -> !contains(o.getOrderNo(), orderNo));
        list.sort(Comparator.comparing(OutboundOrder::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return ok(list);
    }

    @GetMapping("/kanban/scan/{kanbanNo}")
    public Map<String, Object> getOutboundKanban(@PathVariable String kanbanNo) {
        return outboundKanbanRepo.findByKanbanNo(kanbanNo)
                .map(k -> ok((Object) k))
                .orElse(fail("出库看板不存在"));
    }

    @GetMapping("/order/{id}/kanbans")
    public Map<String, Object> getOrderKanbans(@PathVariable Long id) {
        return ok(outboundKanbanRepo.findByOrderId(id));
    }

    @GetMapping("/stock-availability")
    public Map<String, Object> stockAvailability(@RequestParam(required = false) Long supplierId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (var part : partRepo.findAll()) {
            if (supplierId != null && !Objects.equals(part.getSupplierId(), supplierId)) continue;
            var stockKanbans = availableStockKanbans(part.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("partId", part.getId());
            item.put("partCode", part.getCode());
            item.put("partName", part.getName());
            item.put("unit", part.getUnit());
            item.put("supplierId", part.getSupplierId());
            item.put("supplierName", part.getSupplierName());
            item.put("availableQty", stockKanbans.stream().mapToInt(k -> safe(k.getQty())).sum());
            item.put("warehouseName", joinDistinct(stockKanbans, Kanban::getWarehouseName));
            item.put("locationName", joinDistinct(stockKanbans, Kanban::getLocationName));
            result.add(item);
        }
        result.sort(Comparator.comparing(item -> String.valueOf(item.get("partCode"))));
        return ok(result);
    }

    @PostMapping("/order")
    @Transactional
    public Map<String, Object> createOrder(@RequestBody OutboundOrder order) {
        if (order.getCustomerId() == null) return fail("请选择客户");
        var customer = customerRepo.findById(order.getCustomerId()).orElse(null);
        if (customer == null) return fail("客户不存在");

        if (order.getSupplierId() == null) return fail("请选择供应商");
        var supplier = supplierRepo.findById(order.getSupplierId()).orElse(null);
        if (supplier == null) return fail("供应商不存在");

        String outboundType = text(order.getOutboundType());
        if (outboundType.isBlank()) outboundType = "出库";
        if ("调账退货(无实物)".equals(outboundType)) outboundType = "调账退货（无实物）";
        if (!OUTBOUND_TYPES.contains(outboundType)) return fail("出库类型不正确");
        if (order.getItems() == null || order.getItems().isEmpty()) return fail("至少添加一条出库明细");

        order.setId(null);
        order.setOrderNo("OUT" + System.currentTimeMillis());
        order.setCustomerName(customer.getName());
        order.setSupplierName(supplier.getName());
        order.setOutboundType(outboundType);
        order.setStatus("PENDING");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        Map<Long, Integer> requestedQty = new LinkedHashMap<>();
        for (var item : order.getItems()) {
            if (item.getPartId() == null || item.getPlanQty() == null || item.getPlanQty() <= 0) {
                return fail("出库明细不完整");
            }
            var part = partRepo.findById(item.getPartId()).orElse(null);
            if (part == null) return fail("零件不存在: " + item.getPartCode());
            if (!Objects.equals(part.getSupplierId(), supplier.getId())) {
                return fail("零件 " + part.getCode() + " 不属于所选供应商");
            }
            requestedQty.merge(part.getId(), safe(item.getPlanQty()), Integer::sum);
        }
        for (var entry : requestedQty.entrySet()) {
            int available = availableStockQty(entry.getKey());
            if (available < entry.getValue()) {
                var part = partRepo.findById(entry.getKey()).orElse(null);
                String partText = part == null ? String.valueOf(entry.getKey()) : part.getCode() + " " + part.getName();
                return fail("零件 " + partText + " 库存不足，当前可用库存 " + available + "，计划出库 " + entry.getValue());
            }
        }

        for (var item : order.getItems()) {
            var part = partRepo.findById(item.getPartId()).orElseThrow();
            item.setId(null);
            item.setPartCode(part.getCode());
            item.setPartName(part.getName());
            item.setUnit(part.getUnit());
            item.setKanbanNo(nextOutboundKanbanNo());
            item.setSupplierName(supplier.getName());
            item.setActualQty(0);

            if (item.getWarehouseId() != null) {
                var wh = warehouseRepo.findById(item.getWarehouseId()).orElse(null);
                if (wh != null) item.setWarehouseName(wh.getName());
            }
            if (item.getLocationId() != null) {
                var loc = locationRepo.findById(item.getLocationId()).orElse(null);
                if (loc != null) item.setLocationName(loc.getCode());
            }
            var stockKanbans = availableStockKanbans(item.getPartId());
            if (text(item.getWarehouseName()).isBlank()) item.setWarehouseName(joinDistinct(stockKanbans, Kanban::getWarehouseName));
            if (text(item.getLocationName()).isBlank()) item.setLocationName(joinDistinct(stockKanbans, Kanban::getLocationName));
        }

        var saved = orderRepo.save(order);
        for (var item : saved.getItems()) {
            OutboundKanban kanban = new OutboundKanban();
            kanban.setKanbanNo(item.getKanbanNo());
            kanban.setOrderId(saved.getId());
            kanban.setOrderNo(saved.getOrderNo());
            kanban.setCustomerName(saved.getCustomerName());
            kanban.setOutboundType(saved.getOutboundType());
            kanban.setPartId(item.getPartId());
            kanban.setPartCode(item.getPartCode());
            kanban.setPartName(item.getPartName());
            kanban.setActualQty(safe(item.getPlanQty()));
            kanban.setUnit(item.getUnit());
            kanban.setSupplierName(saved.getSupplierName());
            kanban.setWarehouseName(item.getWarehouseName());
            kanban.setLocationName(item.getLocationName());
            kanban.setStatus("PRINTED");
            kanban.setPrintTime(LocalDateTime.now());
            outboundKanbanRepo.save(kanban);
        }
        return ok("出库单创建成功，已自动生成出库看板", saved);
    }

    @PutMapping("/order/{id}/void")
    @Transactional
    public Map<String, Object> voidOrder(@PathVariable Long id) {
        var order = orderRepo.findById(id).orElse(null);
        if (order == null) return fail("出库单不存在");
        order.setStatus("VOIDED");
        order.setUpdateTime(LocalDateTime.now());
        for (var kanban : outboundKanbanRepo.findByOrderId(order.getId())) {
            if (!"OUTBOUND".equals(kanban.getStatus())) {
                kanban.setStatus("VOIDED");
                outboundKanbanRepo.save(kanban);
            }
        }
        return ok("出库单已作废", orderRepo.save(order));
    }

    @DeleteMapping("/order/{id}")
    @Transactional
    public Map<String, Object> deleteOrder(@PathVariable Long id) {
        var order = orderRepo.findById(id).orElse(null);
        if (order == null) return fail("出库单不存在");
        inventoryRepo.deleteAll(inventoryRepo.findByRefOrderNo(order.getOrderNo()));
        var kanbans = outboundKanbanRepo.findByOrderId(id);
        outboundKanbanRepo.deleteAll(kanbans);
        orderRepo.deleteById(id);
        return ok("出库单及相关看板、库存记录已全部删除");
    }

    @PostMapping("/scan")
    @Transactional
    public Map<String, Object> scanOutbound(@RequestBody Map<String, Object> body) {
        String scannedNo = text(body.get("kanbanNo"));
        String operator = text(body.get("operator"));
        var outboundKanban = outboundKanbanRepo.findByKanbanNo(scannedNo).orElse(null);
        if (outboundKanban == null) return fail("出库看板不存在");
        if (!"PRINTED".equals(outboundKanban.getStatus())) {
            return fail("当前出库看板状态不允许出库");
        }

        int outboundQty = safe(outboundKanban.getActualQty());
        if (outboundQty <= 0) return fail("出库数量不正确");

        var sourceKanbans = availableStockKanbans(outboundKanban.getPartId());
        if (sourceKanbans.isEmpty()) return fail("该零件无可用库存");

        int availableQty = sourceKanbans.stream().mapToInt(k -> safe(k.getQty())).sum();
        if (outboundQty > availableQty) {
            return fail("出库数量超过库存数量，当前可用库存 " + availableQty + "，需要出库 " + outboundQty);
        }

        LocalDateTime outboundTime = LocalDateTime.now();
        String outboundOperator = operator.isBlank() ? "admin" : operator;
        int remaining = outboundQty;
        List<String> consumedNos = new ArrayList<>();
        List<Kanban> consumedKanbans = new ArrayList<>();
        List<Integer> consumedQtys = new ArrayList<>();
        for (var source : sourceKanbans) {
            if (remaining <= 0) break;
            int sourceQty = safe(source.getQty());
            if (sourceQty <= 0) continue;
            int takeQty = Math.min(remaining, sourceQty);
            consumedNos.add(source.getKanbanNo());
            consumedKanbans.add(source);
            consumedQtys.add(takeQty);
            if (takeQty >= sourceQty) {
                source.setStatus("OUTBOUND");
            } else {
                source.setQty(sourceQty - takeQty);
            }
            source.setOutboundTime(outboundTime);
            source.setOutboundOperator(outboundOperator);
            source.setOutboundOrderNo(outboundKanban.getOrderNo());
            kanbanRepo.save(source);
            remaining -= takeQty;
        }

        outboundKanban.setStatus("OUTBOUND");
        outboundKanban.setSourceKanbanNo(String.join(",", consumedNos));
        outboundKanban.setOutboundTime(outboundTime);
        outboundKanban.setOutboundOperator(outboundOperator);
        outboundKanbanRepo.save(outboundKanban);

        var order = orderRepo.findByOrderNo(outboundKanban.getOrderNo()).orElse(null);
        if (order != null) {
            order.getItems().stream()
                    .filter(i -> Objects.equals(i.getKanbanNo(), scannedNo))
                    .findFirst()
                    .ifPresent(item -> {
                        item.setActualQty(safe(item.getActualQty()) + outboundQty);
                        item.setSourceKanbanNo(String.join(",", consumedNos));
                    });
            updateOrderStatus(order);
            orderRepo.save(order);
        }

        for (int i = 0; i < consumedKanbans.size(); i++) {
            var source = consumedKanbans.get(i);
            InventoryRecord record = new InventoryRecord();
            record.setPartId(outboundKanban.getPartId());
            record.setPartCode(outboundKanban.getPartCode());
            record.setPartName(outboundKanban.getPartName());
            record.setUnit(outboundKanban.getUnit());
            record.setKanbanNo(source.getKanbanNo());
            var location = findLocationByCode(source.getLocationName());
            if (location != null) record.setLocationId(location.getId());
            record.setLocationName(source.getLocationName());
            record.setQty(consumedQtys.get(i));
            record.setType("OUTBOUND");
            record.setRefOrderNo(outboundKanban.getOrderNo());
            record.setCreateTime(LocalDateTime.now());
            inventoryRepo.save(record);
        }

        return ok("扫码出库成功", Map.of("kanban", outboundKanban));
    }

    @PostMapping("/direct")
    @Transactional
    public Map<String, Object> directOutbound(@RequestBody Map<String, Object> body) {
        String kanbanNo = text(body.get("kanbanNo"));
        String operator = text(body.get("operator"));
        int qty = parseInt(body.get("qty"), 0);
        if (kanbanNo.isBlank()) return fail("请输入库存看板号");

        var source = kanbanRepo.findByKanbanNo(kanbanNo).orElse(null);
        if (source == null) return fail("库存看板不存在");
        if (!"SCANNED".equals(source.getStatus())) return fail("只有已入库库存看板才能不带单出库");
        if (Boolean.TRUE.equals(source.getSealed())) return fail("库存看板已封存，不能出库");

        int available = safe(source.getQty());
        if (available <= 0) return fail("该看板没有可出库库存");
        if (qty <= 0) qty = available;
        if (qty > available) return fail("出库数量超过库存数量，当前可用库存 " + available + "，需要出库 " + qty);

        LocalDateTime outboundTime = LocalDateTime.now();
        String outboundOperator = operator.isBlank() ? "admin" : operator;
        String directOrderNo = "DIRECT" + System.currentTimeMillis();
        if (qty >= available) {
            source.setStatus("OUTBOUND");
        } else {
            source.setQty(available - qty);
        }
        source.setOutboundTime(outboundTime);
        source.setOutboundOperator(outboundOperator);
        source.setOutboundOrderNo(directOrderNo);
        kanbanRepo.save(source);

        OutboundKanban outboundKanban = new OutboundKanban();
        outboundKanban.setKanbanNo(nextOutboundKanbanNo());
        outboundKanban.setOrderNo(directOrderNo);
        outboundKanban.setCustomerName("不带单出库");
        outboundKanban.setOutboundType("不带单出库");
        outboundKanban.setSourceKanbanNo(source.getKanbanNo());
        outboundKanban.setPartId(source.getPartId());
        outboundKanban.setPartCode(source.getPartCode());
        outboundKanban.setPartName(source.getPartName());
        outboundKanban.setActualQty(qty);
        outboundKanban.setUnit(source.getUnit());
        outboundKanban.setSupplierName(source.getSupplierName());
        outboundKanban.setWarehouseName(source.getWarehouseName());
        outboundKanban.setLocationName(source.getLocationName());
        outboundKanban.setStatus("OUTBOUND");
        outboundKanban.setPrintTime(outboundTime);
        outboundKanban.setOutboundTime(outboundTime);
        outboundKanban.setOutboundOperator(outboundOperator);
        outboundKanbanRepo.save(outboundKanban);

        InventoryRecord record = new InventoryRecord();
        record.setPartId(source.getPartId());
        record.setPartCode(source.getPartCode());
        record.setPartName(source.getPartName());
        record.setUnit(source.getUnit());
        record.setKanbanNo(source.getKanbanNo());
        record.setLocationName(source.getLocationName());
        record.setQty(qty);
        record.setType("OUTBOUND_DIRECT");
        record.setRefOrderNo(directOrderNo);
        record.setCreateTime(outboundTime);
        inventoryRepo.save(record);

        return ok("不带单出库成功", Map.of("kanban", source, "outboundKanban", outboundKanban, "qty", qty));
    }

    @PostMapping("/return")
    @Transactional
    public Map<String, Object> returnStock(@RequestBody Map<String, Object> body) {
        String kanbanNo = text(body.get("kanbanNo"));
        String operator = text(body.get("operator"));
        int qty = parseInt(body.get("qty"), 0);
        if (kanbanNo.isBlank()) return fail("请输入出库看板号");

        var outboundKanban = outboundKanbanRepo.findByKanbanNo(kanbanNo).orElse(null);
        if (outboundKanban == null) return fail("出库看板不存在");
        if (!"OUTBOUND".equals(outboundKanban.getStatus())) return fail("只有已出库看板才能退库");

        int outboundQty = safe(outboundKanban.getActualQty());
        if (outboundQty <= 0) return fail("该看板没有可退库数量");
        if (qty <= 0) qty = outboundQty;
        if (qty > outboundQty) return fail("退库数量不能超过已出库数量");

        LocalDateTime now = LocalDateTime.now();
        Kanban returned = new Kanban();
        returned.setKanbanNo(nextStockKanbanNo());
        returned.setOrderId(outboundKanban.getOrderId());
        returned.setOrderNo(outboundKanban.getOrderNo());
        returned.setPartId(outboundKanban.getPartId());
        returned.setPartCode(outboundKanban.getPartCode());
        returned.setPartName(outboundKanban.getPartName());
        returned.setQty(qty);
        returned.setUnit(outboundKanban.getUnit());
        returned.setWarehouseName(outboundKanban.getWarehouseName());
        returned.setLocationName(outboundKanban.getLocationName());
        returned.setStatus("SCANNED");
        returned.setPrintTime(now);
        returned.setScanTime(now);
        returned.setScanner(operator.isBlank() ? "admin" : operator);
        returned.setSealed(false);
        returned.setSourceKanbanNo(outboundKanban.getKanbanNo());
        returned = kanbanRepo.save(returned);

        InventoryRecord record = new InventoryRecord();
        record.setPartId(returned.getPartId());
        record.setPartCode(returned.getPartCode());
        record.setPartName(returned.getPartName());
        record.setUnit(returned.getUnit());
        record.setKanbanNo(returned.getKanbanNo());
        record.setLocationName(returned.getLocationName());
        record.setQty(qty);
        record.setType("RETURN_INBOUND");
        record.setRefOrderNo(outboundKanban.getOrderNo());
        record.setCreateTime(now);
        inventoryRepo.save(record);

        return ok("退库成功", Map.of("kanban", returned));
    }

    @GetMapping("/recent")
    public Map<String, Object> recent() {
        List<OutboundKanban> list = outboundKanbanRepo.findAll().stream()
                .filter(k -> "OUTBOUND".equals(k.getStatus()))
                .sorted(Comparator.comparing(OutboundKanban::getOutboundTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(50).toList();
        return ok(list);
    }

    @GetMapping("/history")
    public Map<String, Object> history(@RequestParam(defaultValue = "") String orderNo) {
        List<OutboundKanban> list = outboundKanbanRepo.findAll().stream()
                .filter(k -> "OUTBOUND".equals(k.getStatus()))
                .filter(k -> orderNo.isBlank() || contains(k.getOrderNo(), orderNo))
                .sorted(Comparator.comparing(OutboundKanban::getOutboundTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return ok(list);
    }


    @GetMapping("/pending")
    public Map<String, Object> pendingKanbans() {
        List<OutboundKanban> list = outboundKanbanRepo.findAll().stream()
                .filter(k -> "PRINTED".equals(k.getStatus()))
                .sorted(Comparator.comparing(OutboundKanban::getPrintTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return ok(list);
    }

    private void updateOrderStatus(OutboundOrder order) {
        boolean completed = order.getItems().stream().allMatch(i -> safe(i.getActualQty()) >= safe(i.getPlanQty()));
        boolean partial = order.getItems().stream().anyMatch(i -> safe(i.getActualQty()) > 0);
        order.setStatus(completed ? "COMPLETED" : partial ? "PARTIAL" : "PENDING");
        order.setUpdateTime(LocalDateTime.now());
    }

    private String nextOutboundKanbanNo() {
        String no;
        do {
            no = "OKB" + System.currentTimeMillis() + String.format("%03d", new Random().nextInt(1000));
        } while (outboundKanbanRepo.findByKanbanNo(no).isPresent());
        return no;
    }

    private String nextStockKanbanNo() {
        return "KB" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private List<Kanban> availableStockKanbans(Long partId) {
        return kanbanRepo.findByPartIdAndStatus(partId, "SCANNED").stream()
                .filter(k -> !Boolean.TRUE.equals(k.getSealed()))
                .filter(k -> safe(k.getQty()) > 0)
                .sorted(Comparator.comparing(Kanban::getScanTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private int availableStockQty(Long partId) {
        return availableStockKanbans(partId).stream().mapToInt(k -> safe(k.getQty())).sum();
    }

    private String joinDistinct(List<Kanban> kanbans, java.util.function.Function<Kanban, String> getter) {
        return kanbans.stream()
                .map(getter)
                .map(this::text)
                .filter(value -> !value.isBlank())
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private Location findLocationByCode(String code) {

        String normalized = text(code);
        if (normalized.isBlank()) return null;
        return locationRepo.findAll().stream()
                .filter(location -> normalized.equalsIgnoreCase(text(location.getCode()))
                        || normalized.equalsIgnoreCase(text(location.getName())))
                .findFirst().orElse(null);
    }

    private int parseInt(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int safe(Integer value) { return value == null ? 0 : value; }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private boolean contains(String value, String keyword) {
        return keyword == null || keyword.isBlank()
                || (value != null && value.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT)));
    }
    private Map<String, Object> fail(String message) { return Map.of("code", 400, "message", message); }
    private Map<String, Object> ok(Object data) { return Map.of("code", 200, "data", data); }
    private Map<String, Object> ok(String message, Object data) {
        return Map.of("code", 200, "message", message, "data", data);
    }
}
