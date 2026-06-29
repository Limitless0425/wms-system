package com.example.demo.controller;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/kanban")
public class KanbanController {

    private final KanbanRepository kanbanRepo;
    private final InboundOrderRepository orderRepo;
    private final InventoryRecordRepository inventoryRepo;
    private final ContainerRepository containerRepo;
    private final RepackRecordRepository repackRepo;
    private final OutboundKanbanRepository outboundKanbanRepo;
    private final RepackKanbanRepository repackKanbanRepo;
    private final RepackOrderRepository repackOrderRepo;

    public KanbanController(KanbanRepository kanbanRepo, InboundOrderRepository orderRepo,
                            InventoryRecordRepository inventoryRepo, ContainerRepository containerRepo,
                            RepackRecordRepository repackRepo, OutboundKanbanRepository outboundKanbanRepo,
                            RepackKanbanRepository repackKanbanRepo,
                            RepackOrderRepository repackOrderRepo) {
        this.kanbanRepo = kanbanRepo;
        this.orderRepo = orderRepo;
        this.inventoryRepo = inventoryRepo;
        this.containerRepo = containerRepo;
        this.repackRepo = repackRepo;
        this.outboundKanbanRepo = outboundKanbanRepo;
        this.repackKanbanRepo = repackKanbanRepo;
        this.repackOrderRepo = repackOrderRepo;
    }

    @PostMapping("/generate/{orderId}")
    public Map<String, Object> generateKanban(@PathVariable Long orderId) {
        var order = orderRepo.findById(orderId).orElse(null);
        if (order == null) return fail(404, "入库单不存在");
        if (Boolean.TRUE.equals(order.getManualInbound())) return fail(400, "该入库单已手工入库，不能生成看板");

        kanbanRepo.findByOrderId(orderId).stream()
                .filter(k -> "PRINTED".equals(k.getStatus()))
                .forEach(kanbanRepo::delete);
        List<Kanban> list = buildKanbans(order);
        if (list.isEmpty()) return fail(400, "所有零件已入库完毕");
        if ("DRAFT".equals(order.getStatus())) {
            order.setStatus("CONFIRMED");
            orderRepo.save(order);
        }
        return ok("看板生成成功，共 " + list.size() + " 张", list);
    }

    public List<Kanban> buildKanbans(InboundOrder order) {
        List<Kanban> kanbans = new ArrayList<>();
        if (Boolean.TRUE.equals(order.getManualInbound())) return kanbans;
        for (var item : order.getItems()) {
            int remaining = safe(item.getPlanQty()) - safe(item.getActualQty());
            if (remaining <= 0) continue;
            int packageCapacity = packageCapacity(item);
            if (packageCapacity <= 0) {
                kanbans.add(kanbanRepo.save(createKanban(order, item, remaining)));
                continue;
            }
            while (remaining > 0) {
                int qty = Math.min(packageCapacity, remaining);
                kanbans.add(kanbanRepo.save(createKanban(order, item, qty)));
                remaining -= qty;
            }
        }
        return kanbans;
    }

    @GetMapping("/list")
    public Map<String, Object> listKanbans(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String inboundOrderNo,
            @RequestParam(defaultValue = "") String warehouse,
            @RequestParam(defaultValue = "") String kanbanNo,
            @RequestParam(defaultValue = "") String supplier,
            @RequestParam(defaultValue = "") String partNo) {
        List<Kanban> list = new ArrayList<>(kanbanRepo.findAll());
        Map<Long, InboundOrder> orders = orderRepo.findAll().stream()
                .collect(Collectors.toMap(InboundOrder::getId, o -> o));
        list.forEach(k -> {
            InboundOrder order = orders.get(k.getOrderId());
            if (order != null) {
                k.setSupplierName(order.getSupplierName());
                if (k.getWarehouseName() == null) k.setWarehouseName(order.getWarehouseName());
            }
        });
        if (!status.isBlank()) list.removeIf(k -> !matchesStatus(k, status));
        if (!inboundOrderNo.isBlank()) list.removeIf(k -> !contains(k.getOrderNo(), inboundOrderNo));
        if (!warehouse.isBlank()) list.removeIf(k -> !contains(k.getWarehouseName(), warehouse));
        if (!kanbanNo.isBlank()) list.removeIf(k -> !contains(k.getKanbanNo(), kanbanNo));
        if (!supplier.isBlank()) list.removeIf(k -> !contains(k.getSupplierName(), supplier));
        if (!partNo.isBlank()) list.removeIf(k -> !contains(k.getPartCode(), partNo));
        list.sort(Comparator.comparing(this::latestTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return Map.of("code", 200, "data", list);
    }

    @GetMapping("/all")
    public Map<String, Object> listAllKanbans(
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String inboundOrderNo,
            @RequestParam(defaultValue = "") String warehouse,
            @RequestParam(defaultValue = "") String kanbanNo,
            @RequestParam(defaultValue = "") String supplier,
            @RequestParam(defaultValue = "") String partNo) {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<Long, InboundOrder> orders = orderRepo.findAll().stream()
                .collect(Collectors.toMap(InboundOrder::getId, o -> o));

        for (var k : kanbanRepo.findAll()) {
            InboundOrder order = orders.get(k.getOrderId());
            if (order != null) {
                k.setSupplierName(order.getSupplierName());
                if (k.getWarehouseName() == null) k.setWarehouseName(order.getWarehouseName());
            }
            list.add(inboundKanbanRow(k));
        }
        for (var k : outboundKanbanRepo.findAll()) list.add(outboundKanbanRow(k));
        for (var k : repackKanbanRepo.findAll()) list.add(repackKanbanRow(k));

        if (!status.isBlank()) list.removeIf(k -> !matchesUnifiedStatus(k, status));
        if (!inboundOrderNo.isBlank()) list.removeIf(k -> !contains(text(k.get("orderNo")), inboundOrderNo));
        if (!warehouse.isBlank()) list.removeIf(k -> !contains(text(k.get("warehouseName")), warehouse));
        if (!kanbanNo.isBlank()) list.removeIf(k -> !contains(text(k.get("kanbanNo")), kanbanNo));
        if (!supplier.isBlank()) list.removeIf(k -> !contains(text(k.get("supplierName")), supplier));
        if (!partNo.isBlank()) list.removeIf(k -> !contains(text(k.get("partCode")), partNo));
        list.sort(Comparator.comparing(row -> (LocalDateTime) row.get("latestTime"), Comparator.nullsLast(Comparator.reverseOrder())));
        return Map.of("code", 200, "data", list);
    }

    @GetMapping("/scan/{kanbanNo}")
    public Map<String, Object> getByNo(@PathVariable String kanbanNo) {
        return kanbanRepo.findByKanbanNo(kanbanNo)
                .map(k -> {
                    enrichKanban(k);
                    return Map.of("code", 200, "data", (Object) k);
                })
                .orElse(fail(404, "看板不存在"));
    }

    @PostMapping("/scan/{kanbanNo}")
    public Map<String, Object> scanInbound(@PathVariable String kanbanNo, @RequestBody Map<String, String> body) {
        var kb = kanbanRepo.findByKanbanNo(kanbanNo).orElse(null);
        if (kb == null) return fail(404, "看板不存在");
        if (Boolean.TRUE.equals(kb.getSealed())) return fail(400, "该看板已封存，解封后才能入库");
        if ("SCANNED".equals(kb.getStatus())) return fail(400, "该看板已入库，请勿重复扫码");
        if (!"PRINTED".equals(kb.getStatus())) return fail(400, "当前看板状态不允许入库");

        int originalQty = safe(kb.getQty());
        int qty;
        try {
            qty = Integer.parseInt(body.getOrDefault("qty", String.valueOf(originalQty)));
        } catch (NumberFormatException e) {
            return fail(400, "入库数量格式不正确");
        }
        if (qty <= 0 || qty > originalQty) return fail(400, "入库数量必须大于 0 且不能超过看板数量");

        kb.setStatus("SCANNED");
        kb.setScanTime(LocalDateTime.now());
        kb.setScanner(body.getOrDefault("scanner", "admin"));
        kb.setQty(qty);
        if (kb.getSealed() == null) kb.setSealed(false);
        kanbanRepo.save(kb);

        var order = orderRepo.findById(kb.getOrderId()).orElse(null);
        Kanban balanceKanban = null;
        if (order != null) {
            order.getItems().stream()
                    .filter(item -> Objects.equals(item.getPartId(), kb.getPartId()))
                    .findFirst()
                    .ifPresent(item -> item.setActualQty(safe(item.getActualQty()) + qty));
            refreshInboundOrderStatus(order.getId());
            if (qty < originalQty) {
                balanceKanban = cloneKanban(kb, originalQty - qty, "PRINTED");
                balanceKanban.setScanTime(null);
                balanceKanban.setScanner(null);
                balanceKanban.setSourceKanbanNo(kb.getKanbanNo());
                kanbanRepo.save(balanceKanban);
                refreshInboundOrderStatus(order.getId());
            }
        }

        inventoryRepo.save(inventoryRecord(kb, qty, "INBOUND", kb.getOrderNo()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kanban", kb);
        data.put("order", order);
        data.put("balanceKanban", balanceKanban);
        String message = balanceKanban == null ? "扫码入库成功" : "扫码入库成功，已生成剩余数量看板";
        return ok(message, data);
    }

    @PutMapping("/{id}/seal")
    public Map<String, Object> seal(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        var kb = kanbanRepo.findById(id).orElse(null);
        if (kb == null) return fail(404, "看板不存在");
        if ("OUTBOUND".equals(kb.getStatus()) || "VOIDED".equals(kb.getStatus()) || "REPACKED".equals(kb.getStatus())) {
            return fail(400, "当前看板状态不能封存");
        }
        kb.setSealed(true);
        kb.setSealTime(LocalDateTime.now());
        kb.setSealReason(body == null ? "" : body.getOrDefault("reason", ""));
        kanbanRepo.save(kb);
        return ok("看板已封存", kb);
    }

    @PutMapping("/{id}/unseal")
    public Map<String, Object> unseal(@PathVariable Long id) {
        var kb = kanbanRepo.findById(id).orElse(null);
        if (kb == null) return fail(404, "看板不存在");
        kb.setSealed(false);
        kb.setSealTime(null);
        kb.setSealReason(null);
        kanbanRepo.save(kb);
        return ok("看板已解封", kb);
    }

    @GetMapping("/repack-records")
    public Map<String, Object> repackRecords() {
        List<RepackRecord> list = new ArrayList<>(repackRepo.findAll());
        list.sort(Comparator.comparing(RepackRecord::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return Map.of("code", 200, "data", list);
    }

    @GetMapping("/repack-pending-orders")
    public Map<String, Object> pendingHandRepackOrders() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (var order : repackOrderRepo.findAll()) {
            if (!isHandRepackOrder(order) || "COMPLETED".equals(order.getStatus()) || "VOIDED".equals(order.getStatus())) continue;
            List<RepackKanban> kanbans = repackKanbanRepo.findByOrderId(order.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("order", order);
            row.put("kanbans", kanbans);
            row.put("canCancel", kanbans.stream().allMatch(k -> "PRINTED".equals(k.getStatus())));
            result.add(row);
        }
        result.sort(Comparator.comparing(row -> ((RepackOrder) row.get("order")).getCreateTime(), Comparator.nullsLast(Comparator.reverseOrder())));
        return Map.of("code", 200, "data", result);
    }

    @DeleteMapping("/repack-order/{orderId}")
    @Transactional
    public Map<String, Object> cancelHandRepackOrder(@PathVariable Long orderId) {
        var order = repackOrderRepo.findById(orderId).orElse(null);
        if (order == null) return fail(404, "转包单不存在");
        if (!isHandRepackOrder(order)) return fail(400, "只有手持转包临时单可以在这里撤销");

        List<RepackKanban> kanbans = repackKanbanRepo.findByOrderId(order.getId());
        if (kanbans.stream().anyMatch(k -> !"PRINTED".equals(k.getStatus()))) {
            return fail(400, "转包看板已经扫描记录，不能撤销本次转包");
        }

        int totalQty = order.getItems().stream().mapToInt(i -> safe(i.getPlanQty())).sum();
        restoreHandRepackSource(order, totalQty);
        inventoryRepo.deleteAll(inventoryRepo.findByRefOrderNo(order.getOrderNo()));
        inventoryRepo.deleteAll(inventoryRepo.findByKanbanNo(order.getSourceKanbanNo()).stream()
                .filter(record -> "HAND_REPACK".equals(record.getRefOrderNo()) && "REPACK_OUTBOUND".equals(record.getType()))
                .toList());
        repackKanbanRepo.deleteAll(kanbans);
        repackOrderRepo.delete(order);
        return Map.of("code", 200, "message", "本次手持转包已撤销，来源看板已恢复");
    }

    private Map<String, Object> inboundKanbanRow(Kanban k) {
        Map<String, Object> row = baseRow("INBOUND", "入库/库存看板", k.getId(), k.getKanbanNo(), k.getOrderNo(),
                k.getPartId(), k.getPartCode(), k.getPartName(), k.getQty(), k.getUnit(), k.getSupplierName(),
                k.getWarehouseName(), k.getLocationName(), k.getContainerCode(), k.getStatus(), latestTime(k));
        row.put("sourceKanbanNo", k.getSourceKanbanNo());
        row.put("sealed", Boolean.TRUE.equals(k.getSealed()));
        row.put("canSeal", true);
        return row;
    }

    private Map<String, Object> outboundKanbanRow(OutboundKanban k) {
        Map<String, Object> row = baseRow("OUTBOUND", "出库看板", k.getId(), k.getKanbanNo(), k.getOrderNo(),
                k.getPartId(), k.getPartCode(), k.getPartName(), k.getActualQty(), k.getUnit(), k.getSupplierName(),
                k.getWarehouseName(), k.getLocationName(), null, k.getStatus(),
                k.getOutboundTime() != null ? k.getOutboundTime() : k.getPrintTime());
        row.put("sourceKanbanNo", k.getSourceKanbanNo());
        row.put("customerName", k.getCustomerName());
        row.put("outboundType", k.getOutboundType());
        row.put("sealed", false);
        row.put("canSeal", false);
        return row;
    }

    private Map<String, Object> repackKanbanRow(RepackKanban k) {
        Map<String, Object> row = baseRow("REPACK", "转包看板", k.getId(), k.getKanbanNo(), k.getOrderNo(),
                k.getPartId(), k.getPartCode(), k.getPartName(), k.getQty(), k.getUnit(), k.getSupplierName(),
                k.getWarehouseName(), k.getLocationName(), k.getTargetContainerCode(), k.getStatus(),
                k.getRepackTime() != null ? k.getRepackTime() : k.getPrintTime());
        row.put("sourceKanbanNo", k.getSourceKanbanNo());
        row.put("sourceBusinessType", k.getSourceBusinessType());
        row.put("targetKanbanNo", k.getTargetKanbanNo());
        row.put("sourceContainerCode", k.getSourceContainerCode());
        row.put("targetContainerName", k.getTargetContainerName());
        row.put("sealed", false);
        row.put("canSeal", false);
        return row;
    }

    private Map<String, Object> baseRow(String businessType, String businessLabel, Long id, String kanbanNo,
                                        String orderNo, Long partId, String partCode, String partName,
                                        Integer qty, String unit, String supplierName, String warehouseName,
                                        String locationName, String containerCode, String status,
                                        LocalDateTime latestTime) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("businessType", businessType);
        row.put("businessLabel", businessLabel);
        row.put("id", id);
        row.put("kanbanNo", kanbanNo);
        row.put("orderNo", orderNo);
        row.put("partId", partId);
        row.put("partCode", partCode);
        row.put("partName", partName);
        row.put("qty", qty);
        row.put("unit", unit);
        row.put("supplierName", supplierName);
        row.put("warehouseName", warehouseName);
        row.put("locationName", locationName);
        row.put("containerCode", containerCode);
        row.put("status", status);
        row.put("latestTime", latestTime);
        return row;
    }

    private boolean matchesUnifiedStatus(Map<String, Object> row, String requested) {
        String businessType = text(row.get("businessType"));
        String rowStatus = text(row.get("status"));
        if ("SEALED".equals(requested)) return Boolean.TRUE.equals(row.get("sealed"));
        if ("INBOUND".equals(requested)) return "INBOUND".equals(businessType) && "SCANNED".equals(rowStatus) && !Boolean.TRUE.equals(row.get("sealed"));
        if ("PENDING_SCAN".equals(requested) || "NOT_INBOUND".equals(requested)) return "INBOUND".equals(businessType) && "PRINTED".equals(rowStatus);
        if ("PENDING_OUTBOUND".equals(requested)) return "OUTBOUND".equals(businessType) && "PRINTED".equals(rowStatus);
        if ("TRANSFER_INBOUND".equals(requested)) return "INBOUND".equals(businessType) && row.get("sourceKanbanNo") != null && "SCANNED".equals(rowStatus);
        if ("OUTBOUND".equals(requested)) return ("OUTBOUND".equals(businessType) && "OUTBOUND".equals(rowStatus))
                || ("INBOUND".equals(businessType) && "OUTBOUND".equals(rowStatus));
        if ("REPACK".equals(requested)) return "REPACK".equals(businessType);
        if ("REPACK_PENDING".equals(requested)) return "REPACK".equals(businessType) && "PRINTED".equals(rowStatus);
        if ("REPACKED".equals(requested)) return ("REPACK".equals(businessType) && "REPACKED".equals(rowStatus))
                || ("INBOUND".equals(businessType) && "REPACKED".equals(rowStatus));
        return requested.equals(rowStatus);
    }

    @GetMapping("/repack-source/{kanbanNo}")
    public Map<String, Object> getRepackSource(@PathVariable String kanbanNo) {
        var stock = kanbanRepo.findByKanbanNo(kanbanNo).orElse(null);
        if (stock != null) {
            enrichKanban(stock);
            if (!"PRINTED".equals(stock.getStatus()) && !"SCANNED".equals(stock.getStatus())) {
                return fail(400, "当前入库/库存看板状态不允许转包");
            }
            if (Boolean.TRUE.equals(stock.getSealed())) return fail(400, "来源看板已封存，不能转包");
            return ok("查询成功", repackSourceRow("INBOUND", stock.getId(), stock.getKanbanNo(), stock.getStatus(),
                    stock.getPartId(), stock.getPartCode(), stock.getPartName(), stock.getQty(), stock.getUnit(),
                    stock.getSupplierName(), stock.getWarehouseName(), stock.getLocationName(), stock.getContainerCode()));
        }
        var outbound = outboundKanbanRepo.findByKanbanNo(kanbanNo).orElse(null);
        if (outbound != null) {
            if (!"PRINTED".equals(outbound.getStatus())) return fail(400, "只有未出库的出库看板可以转包出库");
            return ok("查询成功", repackSourceRow("OUTBOUND", outbound.getId(), outbound.getKanbanNo(), outbound.getStatus(),
                    outbound.getPartId(), outbound.getPartCode(), outbound.getPartName(), outbound.getActualQty(), outbound.getUnit(),
                    outbound.getSupplierName(), outbound.getWarehouseName(), outbound.getLocationName(), null));
        }
        return fail(404, "来源看板不存在");
    }

    @PostMapping("/repack")
    @Transactional
    public Map<String, Object> repack(@RequestBody Map<String, String> body) {
        String sourceNo = body.getOrDefault("sourceKanbanNo", "").trim();
        String containerCode = body.getOrDefault("targetContainerCode", "").trim();
        String operator = body.getOrDefault("operator", "admin");
        var container = containerRepo.findAll().stream()
                .filter(c -> c.getCode() != null && c.getCode().equalsIgnoreCase(containerCode))
                .findFirst().orElse(null);
        if (container == null) return fail(400, "目标器具不存在");
        int qty;
        try {
            qty = Integer.parseInt(body.getOrDefault("qty", "0"));
        } catch (NumberFormatException e) {
            return fail(400, "转包数量格式不正确");
        }
        var stock = kanbanRepo.findByKanbanNo(sourceNo).orElse(null);
        if (stock != null) return createRepackFromStockKanban(stock, container, qty, operator);
        var outbound = outboundKanbanRepo.findByKanbanNo(sourceNo).orElse(null);
        if (outbound != null) return createRepackFromOutboundKanban(outbound, container, qty, operator);
        return fail(404, "来源看板不存在");
    }

    @PostMapping("/repack-scan/{kanbanNo}")
    @Transactional
    public Map<String, Object> scanRepackKanban(@PathVariable String kanbanNo, @RequestBody(required = false) Map<String, String> body) {
        var repackKanban = repackKanbanRepo.findByKanbanNo(kanbanNo).orElse(null);
        if (repackKanban == null) return fail(404, "转包看板不存在");
        if (!"PRINTED".equals(repackKanban.getStatus())) return fail(400, "当前转包看板状态不允许扫码");
        var order = repackKanban.getOrderId() == null ? null : repackOrderRepo.findById(repackKanban.getOrderId()).orElse(null);
        String operator = body == null ? "admin" : body.getOrDefault("scanner", body.getOrDefault("operator", "admin"));
        String direction = order == null ? "" : text(order.getRepackDirection());
        LocalDateTime now = LocalDateTime.now();

        if ("转包出库".equals(direction) || "OUTBOUND".equals(repackKanban.getSourceBusinessType())) {
            repackKanban.setStatus("REPACK_OUTBOUND");
            repackKanban.setRepackTime(now);
            repackKanban.setOperator(operator);
            repackKanbanRepo.save(repackKanban);
            inventoryRepo.save(inventoryRecord(repackKanban, safe(repackKanban.getQty()), "REPACK_OUTBOUND", repackKanban.getOrderNo()));
            saveRepackRecord(repackKanban, null, operator);
            updateGeneratedRepackOrder(order, repackKanban);
            return ok("转包出库记录完成", Map.of("repackKanban", repackKanban, "order", order == null ? "" : order));
        }

        repackKanban.setStatus("REPACK_INBOUND");
        repackKanban.setTargetKanbanNo(null);
        repackKanban.setRepackTime(now);
        repackKanban.setOperator(operator);
        repackKanbanRepo.save(repackKanban);
        inventoryRepo.save(inventoryRecord(repackKanban, safe(repackKanban.getQty()), "REPACK_INBOUND", repackKanban.getOrderNo()));
        saveRepackRecord(repackKanban, null, operator);
        updateGeneratedRepackOrder(order, repackKanban);
        return ok("转包入库完成，转包看板已标记为转包入库", Map.of("repackKanban", repackKanban, "order", order == null ? "" : order));
    }

    @PostMapping("/repack-old-disabled")
    public Map<String, Object> legacyRepack(@RequestBody Map<String, String> body) {
        String sourceNo = body.getOrDefault("sourceKanbanNo", "").trim();
        String containerCode = body.getOrDefault("targetContainerCode", "").trim();
        var source = kanbanRepo.findByKanbanNo(sourceNo).orElse(null);
        if (source == null) return fail(404, "来源看板不存在");
        if (!"SCANNED".equals(source.getStatus())) return fail(400, "只有已入库看板可以转包");
        if (Boolean.TRUE.equals(source.getSealed())) return fail(400, "来源看板已封存，不能转包");
        var container = containerRepo.findAll().stream()
                .filter(c -> c.getCode() != null && c.getCode().equalsIgnoreCase(containerCode))
                .findFirst().orElse(null);
        if (container == null) return fail(400, "目标器具不存在");

        int qty;
        try {
            qty = Integer.parseInt(body.getOrDefault("qty", "0"));
        } catch (NumberFormatException e) {
            return fail(400, "转包数量格式不正确");
        }
        int sourceQty = safe(source.getQty());
        if (qty <= 0 || qty > sourceQty) return fail(400, "转包数量必须大于 0 且不能超过来源看板数量");
        if (container.getCapacity() != null && qty > container.getCapacity()) return fail(400, "转包数量超过目标器具容量");
        return createRepackFromStockKanban(source, container, qty, body.getOrDefault("operator", "admin"));
    }

    private Map<String, Object> repackSourceRow(String businessType, Long id, String kanbanNo, String status,
                                                Long partId, String partCode, String partName, Integer qty, String unit,
                                                String supplierName, String warehouseName, String locationName,
                                                String containerCode) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("businessType", businessType);
        row.put("id", id);
        row.put("kanbanNo", kanbanNo);
        row.put("status", status);
        row.put("partId", partId);
        row.put("partCode", partCode);
        row.put("partName", partName);
        row.put("qty", qty);
        row.put("unit", unit);
        row.put("supplierName", supplierName);
        row.put("warehouseName", warehouseName);
        row.put("locationName", locationName);
        row.put("containerCode", containerCode);
        return row;
    }

    private Map<String, Object> createRepackFromStockKanban(Kanban source, Container container, int qty, String operator) {
        enrichKanban(source);
        if (!"PRINTED".equals(source.getStatus()) && !"SCANNED".equals(source.getStatus())) return fail(400, "当前来源看板状态不允许转包");
        if (Boolean.TRUE.equals(source.getSealed())) return fail(400, "来源看板已封存，不能转包");
        int sourceQty = safe(source.getQty());
        if (qty <= 0 || qty > sourceQty) return fail(400, "转包数量必须大于 0 且不能超过来源看板数量");

        int balance = sourceQty - qty;
        boolean stocked = "SCANNED".equals(source.getStatus());
        if (balance == 0) source.setStatus("REPACKED");
        else source.setQty(balance);
        kanbanRepo.save(source);
        refreshInboundOrderStatus(source.getOrderId());
        var order = createGeneratedRepackOrder(source.getKanbanNo(), "INBOUND", "转包入库", source.getPartId(), source.getPartCode(),
                source.getPartName(), source.getUnit(), source.getSupplierName(), source.getWarehouseName(), source.getLocationName(),
                source.getContainerCode(), container, qty, operator);
        if (stocked) inventoryRepo.save(inventoryRecord(source, qty, "REPACK_OUTBOUND", order.getOrderNo()));
        var kanbans = createGeneratedRepackKanbans(order);
        return ok("转包单已生成，请扫描转包看板完成转包入库", Map.of("source", source, "order", order, "kanbans", kanbans, "sourceBalance", balance));
    }

    private Map<String, Object> createRepackFromOutboundKanban(OutboundKanban source, Container container, int qty, String operator) {
        if (!"PRINTED".equals(source.getStatus())) return fail(400, "只有未出库的出库看板可以转包出库");
        int sourceQty = safe(source.getActualQty());
        if (qty <= 0 || qty > sourceQty) return fail(400, "转包数量必须大于 0 且不能超过来源出库看板数量");
        int balance = sourceQty - qty;
        if (balance == 0) source.setStatus("REPACKED");
        else source.setActualQty(balance);
        outboundKanbanRepo.save(source);

        var order = createGeneratedRepackOrder(source.getKanbanNo(), "OUTBOUND", "转包出库", source.getPartId(), source.getPartCode(),
                source.getPartName(), source.getUnit(), source.getSupplierName(), source.getWarehouseName(), source.getLocationName(),
                null, container, qty, operator);
        var kanbans = createGeneratedRepackKanbans(order);
        return ok("转包单已生成，请扫描转包看板完成转包出库", Map.of("source", source, "order", order, "kanbans", kanbans, "sourceBalance", balance));
    }

    private RepackOrder createGeneratedRepackOrder(String sourceKanbanNo, String sourceBusinessType, String direction,
                                                   Long partId, String partCode, String partName, String unit,
                                                   String supplierName, String warehouseName, String locationName,
                                                   String sourceContainerCode, Container targetContainer, int qty,
                                                   String operator) {
        RepackOrder order = new RepackOrder();
        order.setOrderNo(nextRepackOrderNo());
        order.setSupplierName(supplierName);
        order.setRepackDirection(direction);
        order.setAllowBalance(true);
        order.setSourceKanbanNo(sourceKanbanNo);
        order.setSourceBusinessType(sourceBusinessType);
        order.setStatus("PENDING");
        order.setOperator(operator);
        order.setRemark("手持转包自动生成");
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        int capacity = safe(targetContainer.getCapacity());
        int remaining = qty;
        List<RepackOrderItem> items = new ArrayList<>();
        while (remaining > 0) {
            int chunkQty = capacity > 0 ? Math.min(capacity, remaining) : remaining;
            RepackOrderItem item = new RepackOrderItem();
            item.setSourceKanbanNo(sourceKanbanNo);
            item.setSourceBusinessType(sourceBusinessType);
            item.setSourceContainerCode(sourceContainerCode);
            item.setPartId(partId);
            item.setPartCode(partCode);
            item.setPartName(partName);
            item.setUnit(unit);
            item.setPlanQty(chunkQty);
            item.setActualQty(0);
            item.setContainerCode(targetContainer.getCode());
            item.setContainerName(targetContainer.getName());
            item.setWarehouseName(warehouseName);
            item.setLocationName(locationName);
            item.setRepackKanbanNo(nextRepackKanbanNo());
            items.add(item);
            remaining -= chunkQty;
        }
        order.setItems(items);
        return repackOrderRepo.save(order);
    }

    private List<RepackKanban> createGeneratedRepackKanbans(RepackOrder order) {
        List<RepackKanban> kanbans = new ArrayList<>();
        for (var item : order.getItems()) {
            RepackKanban kanban = new RepackKanban();
            kanban.setKanbanNo(item.getRepackKanbanNo());
            kanban.setOrderId(order.getId());
            kanban.setOrderNo(order.getOrderNo());
            kanban.setSourceKanbanNo(item.getSourceKanbanNo());
            kanban.setSourceBusinessType(item.getSourceBusinessType());
            kanban.setPartId(item.getPartId());
            kanban.setPartCode(item.getPartCode());
            kanban.setPartName(item.getPartName());
            kanban.setQty(safe(item.getPlanQty()));
            kanban.setUnit(item.getUnit());
            kanban.setSupplierName(order.getSupplierName());
            kanban.setSourceContainerCode(item.getSourceContainerCode());
            kanban.setTargetContainerCode(item.getContainerCode());
            kanban.setTargetContainerName(item.getContainerName());
            kanban.setWarehouseName(item.getWarehouseName());
            kanban.setLocationName(item.getLocationName());
            kanban.setStatus("PRINTED");
            kanban.setPrintTime(LocalDateTime.now());
            kanban.setOperator(text(order.getOperator()));
            kanbans.add(repackKanbanRepo.save(kanban));
        }
        return kanbans;
    }

    private void updateGeneratedRepackOrder(RepackOrder order, RepackKanban scanned) {
        if (order == null) return;
        for (var item : order.getItems()) {
            if (Objects.equals(item.getRepackKanbanNo(), scanned.getKanbanNo())) {
                item.setActualQty(safe(scanned.getQty()));
            }
        }
        boolean completed = repackKanbanRepo.findByOrderId(order.getId()).stream().noneMatch(k -> "PRINTED".equals(k.getStatus()));
        order.setStatus(completed ? "COMPLETED" : "PROCESSING");
        order.setUpdateTime(LocalDateTime.now());
        repackOrderRepo.save(order);
    }

    private void saveRepackRecord(RepackKanban repackKanban, String targetKanbanNo, String operator) {
        RepackRecord record = new RepackRecord();
        record.setSourceKanbanNo(repackKanban.getSourceKanbanNo());
        record.setTargetKanbanNo(targetKanbanNo == null ? repackKanban.getKanbanNo() : targetKanbanNo);
        record.setPartCode(repackKanban.getPartCode());
        record.setPartName(repackKanban.getPartName());
        record.setQty(safe(repackKanban.getQty()));
        record.setSourceBalance(sourceBalance(repackKanban));
        record.setSourceContainerCode(repackKanban.getSourceContainerCode());
        record.setTargetContainerCode(repackKanban.getTargetContainerCode());
        record.setOperator(operator);
        record.setCreateTime(LocalDateTime.now());
        repackRepo.save(record);
    }

    private int sourceBalance(RepackKanban repackKanban) {
        if ("OUTBOUND".equals(repackKanban.getSourceBusinessType())) {
            return outboundKanbanRepo.findByKanbanNo(repackKanban.getSourceKanbanNo()).map(k -> safe(k.getActualQty())).orElse(0);
        }
        return kanbanRepo.findByKanbanNo(repackKanban.getSourceKanbanNo()).map(k -> "REPACKED".equals(k.getStatus()) ? 0 : safe(k.getQty())).orElse(0);
    }

    private boolean isHandRepackOrder(RepackOrder order) {
        return order != null && text(order.getSourceKanbanNo()).length() > 0 && text(order.getSourceBusinessType()).length() > 0;
    }

    private void restoreHandRepackSource(RepackOrder order, int totalQty) {
        if ("OUTBOUND".equals(order.getSourceBusinessType())) {
            outboundKanbanRepo.findByKanbanNo(order.getSourceKanbanNo()).ifPresent(source -> {
                if (!"REPACKED".equals(source.getStatus())) source.setActualQty(safe(source.getActualQty()) + totalQty);
                source.setStatus("PRINTED");
                outboundKanbanRepo.save(source);
            });
            return;
        }

        kanbanRepo.findByKanbanNo(order.getSourceKanbanNo()).ifPresent(source -> {
            boolean stockedBeforeRepack = source.getScanTime() != null || inventoryRepo.findByKanbanNoAndRefOrderNo(source.getKanbanNo(), order.getOrderNo()).stream()
                    .anyMatch(record -> "REPACK_OUTBOUND".equals(record.getType()));
            if (!"REPACKED".equals(source.getStatus())) source.setQty(safe(source.getQty()) + totalQty);
            source.setStatus(stockedBeforeRepack ? "SCANNED" : "PRINTED");
            kanbanRepo.save(source);
            refreshInboundOrderStatus(source.getOrderId());
        });
    }

    private InventoryRecord inventoryRecord(RepackKanban kb, int qty, String type, String refNo) {
        InventoryRecord record = new InventoryRecord();
        record.setPartId(kb.getPartId());
        record.setPartCode(kb.getPartCode());
        record.setPartName(kb.getPartName());
        record.setUnit(kb.getUnit());
        record.setKanbanNo(kb.getKanbanNo());
        record.setLocationName(kb.getLocationName());
        record.setQty(qty);
        record.setType(type);
        record.setRefOrderNo(refNo);
        record.setCreateTime(LocalDateTime.now());
        return record;
    }

    private String nextRepackOrderNo() {
        String no;
        do {
            no = "ZB" + System.currentTimeMillis() + String.format("%03d", new Random().nextInt(1000));
        } while (repackOrderRepo.findByOrderNo(no).isPresent());
        return no;
    }

    private String nextRepackKanbanNo() {
        String no;
        do {
            no = "RKB" + System.currentTimeMillis() + String.format("%03d", new Random().nextInt(1000));
        } while (repackKanbanRepo.findByKanbanNo(no).isPresent());
        return no;
    }

    private Kanban createKanban(InboundOrder order, InboundOrderItem item, int qty) {
        Kanban kb = new Kanban();
        kb.setKanbanNo(nextKanbanNo());
        kb.setOrderId(order.getId());
        kb.setOrderNo(order.getOrderNo());
        kb.setPartId(item.getPartId());
        kb.setPartCode(item.getPartCode());
        kb.setPartName(item.getPartName());
        kb.setQty(qty);
        kb.setUnit(item.getUnit());
        kb.setWarehouseName(item.getWarehouseName());
        kb.setLocationName(item.getLocationName());
        kb.setContainerCode(item.getContainerCode());
        kb.setContainerName(item.getContainerName());
        kb.setStatus("PRINTED");
        kb.setSealed(false);
        kb.setPrintTime(LocalDateTime.now());
        return kb;
    }

    private Kanban cloneKanban(Kanban source, int qty, String status) {
        Kanban target = new Kanban();
        target.setKanbanNo(nextKanbanNo());
        target.setOrderId(source.getOrderId());
        target.setOrderNo(source.getOrderNo());
        target.setPartId(source.getPartId());
        target.setPartCode(source.getPartCode());
        target.setPartName(source.getPartName());
        target.setQty(qty);
        target.setUnit(source.getUnit());
        target.setWarehouseName(source.getWarehouseName());
        target.setLocationName(source.getLocationName());
        target.setContainerCode(source.getContainerCode());
        target.setContainerName(source.getContainerName());
        target.setStatus(status);
        target.setPrintTime(LocalDateTime.now());
        target.setSealed(false);
        return target;
    }

    private InventoryRecord inventoryRecord(Kanban kb, int qty, String type, String refNo) {
        InventoryRecord record = new InventoryRecord();
        record.setPartId(kb.getPartId());
        record.setPartCode(kb.getPartCode());
        record.setPartName(kb.getPartName());
        record.setUnit(kb.getUnit());
        record.setKanbanNo(kb.getKanbanNo());
        record.setLocationName(kb.getLocationName());
        record.setQty(qty);
        record.setType(type);
        record.setRefOrderNo(refNo);
        record.setCreateTime(LocalDateTime.now());
        return record;
    }

    private void updateOrderStatus(InboundOrder order) {
        boolean allDone = order.getItems().stream().allMatch(i -> safe(i.getActualQty()) >= safe(i.getPlanQty()));
        boolean hasAny = order.getItems().stream().anyMatch(i -> safe(i.getActualQty()) > 0);
        order.setStatus(allDone ? "COMPLETED" : hasAny ? "PARTIAL" : "CONFIRMED");
        order.setUpdateTime(LocalDateTime.now());
    }

    private void refreshInboundOrderStatus(Long orderId) {
        if (orderId == null) return;
        var order = orderRepo.findById(orderId).orElse(null);
        if (order == null || "VOIDED".equals(order.getStatus())) return;
        List<Kanban> kanbans = kanbanRepo.findByOrderId(orderId);
        if (kanbans.isEmpty()) {
            updateOrderStatus(order);
            orderRepo.save(order);
            return;
        }
        boolean hasPending = kanbans.stream().anyMatch(k -> "PRINTED".equals(k.getStatus()));
        boolean hasHandled = kanbans.stream().anyMatch(k -> isInboundKanbanHandled(k.getStatus()));
        if (!hasPending && hasHandled) order.setStatus("COMPLETED");
        else if (hasHandled) order.setStatus("PARTIAL");
        else order.setStatus("CONFIRMED");
        order.setUpdateTime(LocalDateTime.now());
        orderRepo.save(order);
    }

    private boolean isInboundKanbanHandled(String status) {
        return "SCANNED".equals(status) || "REPACKED".equals(status) || "OUTBOUND".equals(status);
    }

    private boolean matchesStatus(Kanban kb, String requested) {
        if ("SEALED".equals(requested)) return Boolean.TRUE.equals(kb.getSealed());
        if ("INBOUND".equals(requested)) return "SCANNED".equals(kb.getStatus()) && !Boolean.TRUE.equals(kb.getSealed());
        if ("PENDING_SCAN".equals(requested) || "NOT_INBOUND".equals(requested)) return "PRINTED".equals(kb.getStatus());
        if ("TRANSFER_INBOUND".equals(requested)) return kb.getSourceKanbanNo() != null && "SCANNED".equals(kb.getStatus());
        return requested.equals(kb.getStatus());
    }

    private void enrichKanban(Kanban kanban) {
        if (kanban.getOrderId() == null) return;
        orderRepo.findById(kanban.getOrderId()).ifPresent(order -> {
            kanban.setSupplierName(order.getSupplierName());
            if (kanban.getWarehouseName() == null || kanban.getWarehouseName().isBlank()) {
                kanban.setWarehouseName(order.getWarehouseName());
            }
        });
    }

    private LocalDateTime latestTime(Kanban kb) {
        if (kb.getOutboundTime() != null) return kb.getOutboundTime();
        if (kb.getScanTime() != null) return kb.getScanTime();
        return kb.getPrintTime();
    }

    private String nextKanbanNo() {
        return "KB" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private int safe(Integer value) { return value == null ? 0 : value; }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    private int packageCapacity(InboundOrderItem item) {
        if (item.getContainerId() != null) {
            var container = containerRepo.findById(item.getContainerId()).orElse(null);
            if (container != null && container.getCapacity() != null && container.getCapacity() > 0) {
                return container.getCapacity();
            }
        }
        if (item.getContainerCode() != null && !item.getContainerCode().isBlank()) {
            return containerRepo.findAll().stream()
                    .filter(c -> c.getCode() != null && c.getCode().equalsIgnoreCase(item.getContainerCode()))
                    .map(Container::getCapacity)
                    .filter(Objects::nonNull)
                    .filter(capacity -> capacity > 0)
                    .findFirst().orElse(0);
        }
        return 0;
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT));
    }

    private Map<String, Object> ok(String message, Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", message);
        result.put("data", data);
        return result;
    }

    private Map<String, Object> fail(int code, String message) {
        return Map.of("code", code, "message", message);
    }
}

