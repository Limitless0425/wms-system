package com.example.demo.controller;





import com.example.demo.entity.*;


import com.example.demo.repository.*;


import org.springframework.transaction.annotation.Transactional;


import org.springframework.web.bind.annotation.*;





import java.time.LocalDateTime;


import java.util.*;





@RestController


@RequestMapping("/api/repack")


public class RepackOrderController {





    private final RepackOrderRepository orderRepo;


    private final RepackKanbanRepository repackKanbanRepo;


    private final KanbanRepository kanbanRepo;


    private final InventoryRecordRepository inventoryRepo;


    private final SupplierRepository supplierRepo;


    private final PartRepository partRepo;


    private final ContainerRepository containerRepo;


    private final WarehouseRepository warehouseRepo;


    private final LocationRepository locationRepo;


    private final OutboundKanbanRepository outboundKanbanRepo;
    private final InboundOrderRepository inboundOrderRepo;





    public RepackOrderController(RepackOrderRepository orderRepo,


                                 RepackKanbanRepository repackKanbanRepo,


                                 KanbanRepository kanbanRepo,


                                 InventoryRecordRepository inventoryRepo,


                                 SupplierRepository supplierRepo,


                                 PartRepository partRepo,


                                 ContainerRepository containerRepo,


                                 WarehouseRepository warehouseRepo,


                                 LocationRepository locationRepo,


                                 OutboundKanbanRepository outboundKanbanRepo,
                                 InboundOrderRepository inboundOrderRepo) {


        this.orderRepo = orderRepo;


        this.repackKanbanRepo = repackKanbanRepo;


        this.kanbanRepo = kanbanRepo;


        this.inventoryRepo = inventoryRepo;


        this.supplierRepo = supplierRepo;


        this.partRepo = partRepo;


        this.containerRepo = containerRepo;


        this.warehouseRepo = warehouseRepo;


        this.locationRepo = locationRepo;


        this.outboundKanbanRepo = outboundKanbanRepo;
        this.inboundOrderRepo = inboundOrderRepo;


    }





    @GetMapping("/orders")


    public Map<String, Object> listOrders(


            @RequestParam(defaultValue = "") String status,


            @RequestParam(defaultValue = "") String orderNo,


            @RequestParam(defaultValue = "") String supplier) {


        List<RepackOrder> list = new ArrayList<>(orderRepo.findAll());


        list.removeIf(o -> isHandRepackOrder(o) && !"COMPLETED".equals(o.getStatus()));


        if (!status.isBlank()) list.removeIf(o -> !status.equals(o.getStatus()));


        if (!orderNo.isBlank()) list.removeIf(o -> !contains(o.getOrderNo(), orderNo));


        if (!supplier.isBlank()) list.removeIf(o -> !contains(o.getSupplierName(), supplier));


        list.sort(Comparator.comparing(RepackOrder::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())));


        return ok(list);


    }





    @PostMapping("/order")


    @Transactional


    public Map<String, Object> createOrder(@RequestBody RepackOrder order) {


        if (order.getSupplierId() == null) return fail("请选择供应商");


        var supplier = supplierRepo.findById(order.getSupplierId()).orElse(null);


        if (supplier == null) return fail("供应商不存在");


        if (order.getItems() == null || order.getItems().isEmpty()) return fail("至少添加一条转包明细");





        order.setId(null);


        order.setOrderNo("ZB" + System.currentTimeMillis());


        order.setSupplierName(supplier.getName());


        if (text(order.getRepackDirection()).isBlank()) order.setRepackDirection("向下转包");


        if (order.getAllowBalance() == null) order.setAllowBalance(true);


        order.setStatus("PENDING");


        order.setCreateTime(LocalDateTime.now());


        order.setUpdateTime(LocalDateTime.now());





        for (var item : order.getItems()) {


            if (item.getPartId() == null || item.getPlanQty() == null || item.getPlanQty() <= 0) {


                return fail("转包明细不完整");


            }


            var part = partRepo.findById(item.getPartId()).orElse(null);


            if (part == null) return fail("零件不存在: " + item.getPartCode());


            if (!Objects.equals(part.getSupplierId(), supplier.getId())) {


                return fail("零件 " + part.getCode() + " 不属于所选供应商");


            }


            item.setId(null);


            item.setPartCode(part.getCode());


            item.setPartName(part.getName());


            item.setUnit(part.getUnit());


            applyRepackRuleDefaults(item, supplier.getId());


            item.setRepackKanbanNo(nextRepackKanbanNo());


            item.setActualQty(0);





            if (item.getWarehouseId() != null) {


                var wh = warehouseRepo.findById(item.getWarehouseId()).orElse(null);


                if (wh != null) item.setWarehouseName(wh.getName());


            }


            if (item.getLocationId() != null) {


                var loc = locationRepo.findById(item.getLocationId()).orElse(null);


                if (loc != null) item.setLocationName(loc.getCode());


            }


            if (item.getContainerCode() != null && !item.getContainerCode().isBlank()) {


                var container = containerRepo.findAll().stream()


                        .filter(c -> c.getCode() != null && c.getCode().equalsIgnoreCase(item.getContainerCode()))


                        .findFirst().orElse(null);


                if (container != null) item.setContainerName(container.getName());


            }


        }





        List<RepackOrderItem> normalizedItems = new ArrayList<>();


        for (var item : order.getItems()) {


            int capacity = repackCapacity(item);


            int qty = safe(item.getPlanQty());


            if (capacity > 0 && qty > capacity) {


                int remaining = qty;


                while (remaining > 0) {


                    int chunkQty = Math.min(capacity, remaining);


                    var split = copyItem(item);


                    split.setId(null);


                    split.setPlanQty(chunkQty);


                    split.setActualQty(0);


                    split.setRepackKanbanNo(nextRepackKanbanNo());


                    normalizedItems.add(split);


                    remaining -= chunkQty;


                }


            } else {


                normalizedItems.add(item);


            }


        }


        order.setItems(normalizedItems);





        Map<Long, Integer> requestedQty = new LinkedHashMap<>();


        for (var item : order.getItems()) {


            requestedQty.merge(item.getPartId(), safe(item.getPlanQty()), Integer::sum);


        }


        for (var entry : requestedQty.entrySet()) {


            int available = availableStockQty(entry.getKey());


            if (available < entry.getValue()) {


                var part = partRepo.findById(entry.getKey()).orElse(null);


                String partText = part == null ? String.valueOf(entry.getKey()) : part.getCode() + " " + part.getName();


                return fail("零件 " + partText + " 可转包库存不足，当前可用库存 " + available + "，计划转包 " + entry.getValue());


            }


        }





        var saved = orderRepo.save(order);


        for (var item : saved.getItems()) {


            RepackKanban kanban = new RepackKanban();


            kanban.setKanbanNo(item.getRepackKanbanNo());


            kanban.setOrderId(saved.getId());


            kanban.setOrderNo(saved.getOrderNo());


            kanban.setPartId(item.getPartId());


            kanban.setPartCode(item.getPartCode());


            kanban.setPartName(item.getPartName());


            kanban.setQty(safe(item.getPlanQty()));


            kanban.setUnit(item.getUnit());


            kanban.setSupplierName(saved.getSupplierName());


            kanban.setTargetContainerCode(item.getContainerCode());


            kanban.setTargetContainerName(item.getContainerName());


            kanban.setTargetContainerType(item.getTargetContainerType());


            kanban.setTargetPackageQty(item.getTargetPackageQty());


            kanban.setWarehouseName(item.getWarehouseName());


            kanban.setLocationName(item.getLocationName());


            kanban.setStatus("PRINTED");


            kanban.setPrintTime(LocalDateTime.now());


            kanban.setOperator(text(saved.getOperator()));


            repackKanbanRepo.save(kanban);


        }


        return ok("转包单创建成功，已自动生成转包看板", saved);


    }





    @GetMapping("/kanban/{kanbanNo}")


    public Map<String, Object> getKanban(@PathVariable String kanbanNo) {


        return repackKanbanRepo.findByKanbanNo(kanbanNo)


                .map(k -> ok((Object) k))


                .orElse(fail("转包看板不存在"));


    }





    @GetMapping("/kanbans")


    public Map<String, Object> listKanbans(@RequestParam(defaultValue = "") String status) {


        List<RepackKanban> list = new ArrayList<>(repackKanbanRepo.findAll());


        if (!status.isBlank()) list.removeIf(k -> !status.equals(k.getStatus()));


        list.sort(Comparator.comparing(RepackKanban::getRepackTime, Comparator.nullsLast(Comparator.reverseOrder())));


        return ok(list);


    }

    @GetMapping("/stock-availability")
    public Map<String, Object> stockAvailability(@RequestParam(required = false) Long supplierId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (var part : partRepo.findAll()) {
            if (supplierId != null && !Objects.equals(part.getSupplierId(), supplierId)) continue;
            var sources = availableStockSources(part.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("partId", part.getId());
            item.put("partCode", part.getCode());
            item.put("partName", part.getName());
            item.put("unit", part.getUnit());
            item.put("supplierId", part.getSupplierId());
            item.put("supplierName", part.getSupplierName());
            item.put("availableQty", sources.stream().mapToInt(StockSource::qty).sum());
            item.put("warehouseName", sources.stream().map(StockSource::warehouseName).filter(s -> !text(s).isBlank()).distinct().collect(java.util.stream.Collectors.joining("、")));
            item.put("locationName", sources.stream().map(StockSource::locationName).filter(s -> !text(s).isBlank()).distinct().collect(java.util.stream.Collectors.joining("、")));
            result.add(item);
        }
        result.sort(Comparator.comparing(item -> String.valueOf(item.get("partCode"))));
        return ok(result);
    }





    @GetMapping("/order/{id}/kanbans")


    public Map<String, Object> getOrderKanbans(@PathVariable Long id) {


        return ok(repackKanbanRepo.findByOrderId(id));


    }





    @GetMapping("/balances")


    public Map<String, Object> balances(


            @RequestParam(defaultValue = "") String orderNo,


            @RequestParam(defaultValue = "") String partCode) {


        List<Map<String, Object>> result = new ArrayList<>();


        for (var order : orderRepo.findAll()) {


            if (!orderNo.isBlank() && !contains(order.getOrderNo(), orderNo)) continue;


            for (var item : order.getItems()) {


                if (!partCode.isBlank() && !contains(item.getPartCode(), partCode)) continue;


                int planQty = safe(item.getPlanQty());


                int actualQty = safe(item.getActualQty());


                Map<String, Object> row = new LinkedHashMap<>();


                row.put("orderNo", order.getOrderNo());


                row.put("status", order.getStatus());


                row.put("supplierName", order.getSupplierName());


                row.put("partCode", item.getPartCode());


                row.put("partName", item.getPartName());


                row.put("planQty", planQty);


                row.put("actualQty", actualQty);


                row.put("balanceQty", Math.max(planQty - actualQty, 0));


                row.put("unit", item.getUnit());


                row.put("updateTime", order.getUpdateTime());


                result.add(row);


            }


        }


        result.sort(Comparator


                .comparing((Map<String, Object> row) -> String.valueOf(row.get("orderNo"))).reversed()


                .thenComparing(row -> String.valueOf(row.get("partCode"))));


        return ok(result);


    }





    @PutMapping("/order/{id}/execute")


    @Transactional


    public Map<String, Object> executeOrder(@PathVariable Long id) {


        var order = orderRepo.findById(id).orElse(null);


        if (order == null) return fail("转包单不存在");


        if (isHandRepackOrder(order)) return fail("手持转包单请在手持转包界面扫描或撤销，不能在转包单管理中执行");


        if ("VOIDED".equals(order.getStatus())) return fail("已作废转包单不能执行");


        if ("COMPLETED".equals(order.getStatus())) return fail("转包单已完成");





        order.setStatus("PROCESSING");


        order.setUpdateTime(LocalDateTime.now());


        orderRepo.save(order);





        for (var item : order.getItems()) {


            int qty = safe(item.getPlanQty());


            if (qty <= 0) return fail("转包数量不正确");





            var sourceKanbans = availableStockSources(item.getPartId());


            int available = sourceKanbans.stream().mapToInt(StockSource::qty).sum();


            if (available < qty) {


                return fail("零件 " + item.getPartCode() + " 可转包库存不足，当前可用 " + available + "，需要 " + qty);


            }





            List<StockSource> candidates = sourceKanbans;


            if (Boolean.FALSE.equals(order.getAllowBalance())) {


                StockSource exact = sourceKanbans.stream()


                        .filter(k -> k.qty() == qty)


                        .findFirst()


                        .orElse(null);


                if (exact == null) {


                    return fail("转包单不允许余量，零件 " + item.getPartCode() + " 未找到数量刚好为 " + qty + " 的来源看板");


                }


                candidates = List.of(exact);


            }





            int remaining = qty;


            List<String> sourceNos = new ArrayList<>();


            for (var source : candidates) {


                if (remaining <= 0) break;


                int sourceQty = source.qty();


                if (sourceQty <= 0) continue;


                int takeQty = Math.min(remaining, sourceQty);


                sourceNos.add(source.kanbanNo());


                if (source.kanban() != null) {


                    if (takeQty >= sourceQty) source.kanban().setStatus("REPACKED");


                    else source.kanban().setQty(sourceQty - takeQty);


                    kanbanRepo.save(source.kanban());


                    inventoryRepo.save(inventoryRecord(source.kanban(), takeQty, "REPACK_OUTBOUND", order.getOrderNo()));


                } else if (source.repackKanban() != null) {


                    if (takeQty >= sourceQty) {


                        source.repackKanban().setStatus("REPACKED");


                        source.repackKanban().setQty(0);


                    } else {


                        source.repackKanban().setQty(sourceQty - takeQty);


                    }


                    repackKanbanRepo.save(source.repackKanban());


                    inventoryRepo.save(inventoryRecord(source.repackKanban(), takeQty, "REPACK_OUTBOUND", order.getOrderNo()));


                }


                remaining -= takeQty;


            }


            if (remaining > 0) return fail("零件 " + item.getPartCode() + " 可转包库存不足");





            item.setActualQty(qty);


            if (item.getRepackKanbanNo() != null) {


                repackKanbanRepo.findByKanbanNo(item.getRepackKanbanNo()).ifPresent(kanban -> {


                    kanban.setStatus("REPACK_INBOUND");


                    kanban.setSourceKanbanNo(String.join(",", sourceNos));


                    kanban.setTargetKanbanNo(null);


                    kanban.setRepackTime(LocalDateTime.now());


                    kanban.setOperator(text(order.getOperator()));


                    repackKanbanRepo.save(kanban);


                    inventoryRepo.save(inventoryRecord(kanban, qty, "REPACK_INBOUND", order.getOrderNo()));


                });


            }


        }





        order.setStatus("COMPLETED");


        order.setUpdateTime(LocalDateTime.now());


        return ok("转包执行完成，已生成转包后的库存看板", orderRepo.save(order));


    }





    @PutMapping("/order/{id}/void")


    @Transactional


    public Map<String, Object> voidOrder(@PathVariable Long id) {


        var order = orderRepo.findById(id).orElse(null);


        if (order == null) return fail("转包单不存在");


        order.setStatus("VOIDED");


        order.setUpdateTime(LocalDateTime.now());


        for (var kanban : repackKanbanRepo.findByOrderId(order.getId())) {


            if (!"REPACKED".equals(kanban.getStatus())) {


                kanban.setStatus("VOIDED");


                repackKanbanRepo.save(kanban);


            }


        }


        return ok("转包单已作废", orderRepo.save(order));


    }





    


    @PostMapping("/scan")


    @Transactional


    public Map<String, Object> scanRepackKanban(@RequestBody Map<String, Object> body) {


        String kanbanNo = text(body.get("kanbanNo"));


        int actualQty = safeInt(body.get("actualQty"));


        String operator = text(body.get("operator"));


        if (kanbanNo.isBlank()) return fail("请输入转包看板号");


        if (actualQty <= 0) return fail("实际转包数量必须大于0");





        var kanban = repackKanbanRepo.findByKanbanNo(kanbanNo).orElse(null);


        if (kanban == null) return fail("转包看板不存在");


        if (!"PRINTED".equals(kanban.getStatus())) return fail("该转包看板已处理，不能重复扫描");





        int planQty = safe(kanban.getQty());


        if (actualQty > planQty) return fail("实际数量不能超过计划数量");





        int balanceQty = planQty - actualQty;





        // Update kanban


        kanban.setActualQty(actualQty);


        kanban.setBalanceQty(balanceQty);


        kanban.setStatus(balanceQty > 0 ? "BALANCE" : "REPACKED");


        kanban.setRepackTime(LocalDateTime.now());


        kanban.setOperator(operator.isBlank() ? "admin" : operator);


        repackKanbanRepo.save(kanban);





        // Generate IN inventory record for target warehouse


        InventoryRecord inRecord = new InventoryRecord();


        inRecord.setPartId(kanban.getPartId());


        inRecord.setPartCode(kanban.getPartCode());


        inRecord.setPartName(kanban.getPartName());


        inRecord.setUnit(kanban.getUnit());


        inRecord.setKanbanNo(kanban.getKanbanNo());


        inRecord.setLocationName(kanban.getLocationName());


        inRecord.setQty(actualQty);


        inRecord.setType("REPACK_IN");


        inRecord.setRefOrderNo(kanban.getOrderNo());


        inRecord.setCreateTime(LocalDateTime.now());


        inventoryRepo.save(inRecord);





        // Generate OUT inventory record for source warehouse (if source info exists)


        String srcWh = text(kanban.getSourceWarehouseName());


        String srcLoc = text(kanban.getSourceLocationName());


        if (!srcWh.isBlank()) {


            InventoryRecord outRecord = new InventoryRecord();


            outRecord.setPartId(kanban.getPartId());


            outRecord.setPartCode(kanban.getPartCode());


            outRecord.setPartName(kanban.getPartName());


            outRecord.setUnit(kanban.getUnit());


            outRecord.setKanbanNo(kanban.getKanbanNo());


            outRecord.setLocationName(srcLoc.isBlank() ? srcWh : srcLoc);


            outRecord.setQty(-actualQty);


            outRecord.setType("REPACK_OUT");


            outRecord.setRefOrderNo(kanban.getOrderNo());


            outRecord.setCreateTime(LocalDateTime.now());


            inventoryRepo.save(outRecord);


        }





        // Update order and item


        var order = orderRepo.findByOrderNo(kanban.getOrderNo()).orElse(null);


        if (order != null) {


            order.getItems().stream()


                    .filter(i -> Objects.equals(i.getRepackKanbanNo(), kanbanNo))


                    .findFirst()


                    .ifPresent(item -> {


                        item.setActualQty(safe(item.getActualQty()) + actualQty);


                    });


            updateOrderStatus(order);


            orderRepo.save(order);


        }





        return ok("扫描成功，实际转包" + actualQty + "，结余" + balanceQty,


                Map.of("kanban", kanban, "balance", balanceQty));


    }





    @DeleteMapping("/order/{id}")


    @Transactional


    public Map<String, Object> deleteOrder(@PathVariable Long id) {


        var order = orderRepo.findById(id).orElse(null);


        if (order == null) return fail("转包单不存在");


        repackKanbanRepo.deleteAll(repackKanbanRepo.findByOrderId(id));


        orderRepo.deleteById(id);


        return ok("转包单已删除");


    }





    private String nextRepackKanbanNo() {


        String no;


        do {


            no = "RKB" + System.currentTimeMillis() + String.format("%03d", new Random().nextInt(1000));


        } while (repackKanbanRepo.findByKanbanNo(no).isPresent());


        return no;


    }





    private String nextStockKanbanNo() {


        return "KB" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();


    }








    @PostMapping("/universal-scan")


    @Transactional


    public Map<String, Object> universalScan(@RequestBody Map<String, Object> body) {


        String kanbanNo = text(body.get("kanbanNo"));


        int actualQty = safeInt(body.get("actualQty"));


        String operator = text(body.get("operator"));


        if (kanbanNo.isBlank()) return fail("请输入转包看板号");


        if (actualQty <= 0) return fail("实际转包数量必须大于0");


        if (operator.isBlank()) operator = "admin";
        Long warehouseId = body.get("warehouseId") != null ? Long.valueOf(String.valueOf(body.get("warehouseId"))) : null;
        Long locationId = body.get("locationId") != null ? Long.valueOf(String.valueOf(body.get("locationId"))) : null;
        String containerCode = text(body.get("containerCode"));





        // 1. Check if it's a repack kanban - execute transfer


        var repackKanban = repackKanbanRepo.findByKanbanNo(kanbanNo).orElse(null);


        if (repackKanban != null) {


            return executeRepackTransfer(repackKanban, actualQty, operator, warehouseId, locationId, containerCode);


        }





        // 2. Check if it's an inbound kanban


        var inboundKanban = kanbanRepo.findByKanbanNo(kanbanNo).orElse(null);


        if (inboundKanban != null) {


            return createRepackFromKanban(inboundKanban, actualQty, operator, "INBOUND", warehouseId, locationId, containerCode);


        }





        // 3. Check if it's an outbound kanban


        var outboundKanban = outboundKanbanRepo.findByKanbanNo(kanbanNo).orElse(null);


        if (outboundKanban != null) {


            return createRepackFromOutboundKanban(outboundKanban, actualQty, operator, warehouseId, locationId, containerCode);


        }





        return fail("未找到该看板，请检查看板号是否正确");


    }





    private Map<String, Object> createRepackFromKanban(Kanban sourceKanban, int qty, String operator, String businessType, Long warehouseId, Long locationId, String containerCode) {


        if (sourceKanban.getPartId() == null) return fail("来源看板缺少零件信息");


        if ("PRINTED".equals(sourceKanban.getStatus()) || "SCANNED".equals(sourceKanban.getStatus())) {


            if (Boolean.TRUE.equals(sourceKanban.getSealed())) return fail("该看板已封存，无法转包");


        } else {


            return fail("该看板状态为 " + sourceKanban.getStatus() + "，无法转包");


        }





        int maxQty = safe(sourceKanban.getQty());


        if (qty > maxQty) return fail("转包数量不能超过看板数量 " + maxQty);





        String orderNo = "ZB" + System.currentTimeMillis();


        RepackOrder order = new RepackOrder();


        order.setOrderNo(orderNo);


        String supplierName = text(sourceKanban.getSupplierName());
        if (supplierName.isEmpty()) {
            supplierName = inboundOrderRepo.findByOrderNo(sourceKanban.getOrderNo())
                    .map(o -> text(o.getSupplierName())).orElse("");
        }
        order.setSupplierName(supplierName);


        order.setRepackDirection("????");


        order.setAllowBalance(true);


        order.setSourceKanbanNo(sourceKanban.getKanbanNo());


        order.setSourceBusinessType(businessType);


        order.setStatus("PENDING");


        order.setOperator(operator);


        order.setCreateTime(LocalDateTime.now());


        order.setUpdateTime(LocalDateTime.now());





        RepackOrderItem item = new RepackOrderItem();


        item.setPartId(sourceKanban.getPartId());


        item.setPartCode(sourceKanban.getPartCode());


        item.setPartName(sourceKanban.getPartName());


        item.setUnit(sourceKanban.getUnit());


        item.setPlanQty(qty);


        item.setActualQty(0);


        item.setSourceKanbanNo(sourceKanban.getKanbanNo());


        item.setSourceBusinessType(businessType);


        item.setSourceContainerCode(sourceKanban.getContainerCode());


                if (containerCode != null && !containerCode.isBlank()) {
            item.setContainerCode(containerCode);
            var con = containerRepo.findAll().stream().filter(c -> containerCode.equalsIgnoreCase(text(c.getCode()))).findFirst().orElse(null);
            if (con != null) item.setContainerName(con.getName());
        }

                // Use target warehouse/location if specified, otherwise use source
        if (warehouseId != null) {
            var wh = warehouseRepo.findById(warehouseId).orElse(null);
            if (wh != null) { item.setWarehouseName(wh.getName()); }
        } else {
            item.setWarehouseName(text(sourceKanban.getWarehouseName()));
        }


                if (locationId != null) {
            var loc = locationRepo.findById(locationId).orElse(null);
            if (loc != null) item.setLocationName(loc.getCode());
        } else {
            item.setLocationName(text(sourceKanban.getLocationName()));
        }


        item.setRepackKanbanNo(nextRepackKanbanNo());





        var supplier = supplierRepo.findAll().stream()


                .filter(s -> Objects.equals(s.getName(), sourceKanban.getSupplierName()))


                .findFirst().orElse(null);


        if (supplier != null) order.setSupplierId(supplier.getId());


        applyRepackRuleDefaults(item, order.getSupplierId());


        if (text(item.getContainerCode()).isBlank()) item.setContainerCode(sourceKanban.getContainerCode());
        if (text(item.getContainerName()).isBlank()) item.setContainerName(sourceKanban.getContainerName());





        order.getItems().add(item);


        var saved = orderRepo.save(order);





        // Generate repack kanban


        int remaining = qty;


        int capacity = repackCapacity(item);


        List<RepackKanban> kanbans = new ArrayList<>();


        while (remaining > 0) {


            int perKanbanQty = capacity > 0 ? Math.min(capacity, remaining) : remaining;


            RepackKanban rk = new RepackKanban();


            rk.setKanbanNo(remaining == qty ? item.getRepackKanbanNo() : nextRepackKanbanNo());


            rk.setOrderId(saved.getId());


            rk.setOrderNo(orderNo);


            rk.setSourceKanbanNo(sourceKanban.getKanbanNo());


            rk.setSourceBusinessType(businessType);


            rk.setPartId(sourceKanban.getPartId());


            rk.setPartCode(sourceKanban.getPartCode());


            rk.setPartName(sourceKanban.getPartName());


            rk.setQty(perKanbanQty);


            rk.setUnit(sourceKanban.getUnit());


            rk.setSupplierName(text(sourceKanban.getSupplierName()));


            rk.setSourceContainerCode(sourceKanban.getContainerCode());


                        rk.setTargetContainerCode(item.getContainerCode());
            rk.setTargetContainerName(item.getContainerName());


            rk.setTargetContainerName(item.getContainerName());


            rk.setTargetContainerType(item.getTargetContainerType());


            rk.setTargetPackageQty(item.getTargetPackageQty());


                        rk.setWarehouseName(item.getWarehouseName());


                        rk.setLocationName(item.getLocationName());


                        rk.setSourceWarehouseName(text(sourceKanban.getWarehouseName()));


                        rk.setSourceLocationName(text(sourceKanban.getLocationName()));


            rk.setStatus("PRINTED");


            rk.setPrintTime(LocalDateTime.now());


            rk.setOperator(operator);


            kanbans.add(repackKanbanRepo.save(rk));


            remaining -= perKanbanQty;


        }





        // Update source kanban


        if (qty >= maxQty) {


            sourceKanban.setStatus("REPACKED");


            sourceKanban.setQty(0);


        } else {


            sourceKanban.setQty(maxQty - qty);


        }


        kanbanRepo.save(sourceKanban);





        return ok("转包单已创建，已生成 " + kanbans.size() + " 张转包看板",


                Map.of("order", saved, "kanbans", kanbans));


    }





    private Map<String, Object> createRepackFromOutboundKanban(OutboundKanban sourceKanban, int qty, String operator, Long warehouseId, Long locationId, String containerCode) {


        if (!"PRINTED".equals(sourceKanban.getStatus())) return fail("该出库看板状态为 " + sourceKanban.getStatus() + "，无法转包");





        int maxQty = safe(sourceKanban.getActualQty());


        if (qty > maxQty) return fail("转包数量不能超过看板数量 " + maxQty);





        String orderNo = "ZB" + System.currentTimeMillis();


        RepackOrder order = new RepackOrder();


        order.setOrderNo(orderNo);


        String supplierName = text(sourceKanban.getSupplierName());
        if (supplierName.isEmpty()) {
            supplierName = inboundOrderRepo.findByOrderNo(sourceKanban.getOrderNo())
                    .map(o -> text(o.getSupplierName())).orElse("");
        }
        order.setSupplierName(supplierName);


        order.setRepackDirection("????");


        order.setAllowBalance(true);


        order.setSourceKanbanNo(sourceKanban.getKanbanNo());


        order.setSourceBusinessType("OUTBOUND");


        order.setStatus("PENDING");


        order.setOperator(operator);


        order.setCreateTime(LocalDateTime.now());


        order.setUpdateTime(LocalDateTime.now());





        RepackOrderItem item = new RepackOrderItem();


        item.setPartId(sourceKanban.getPartId());


        item.setPartCode(sourceKanban.getPartCode());


        item.setPartName(sourceKanban.getPartName());


        item.setUnit(sourceKanban.getUnit());


        item.setPlanQty(qty);


        item.setActualQty(0);


        item.setSourceKanbanNo(sourceKanban.getKanbanNo());


        item.setSourceBusinessType("OUTBOUND");


                // Use target warehouse/location if specified, otherwise use source
        if (warehouseId != null) {
            var wh = warehouseRepo.findById(warehouseId).orElse(null);
            if (wh != null) { item.setWarehouseName(wh.getName()); }
        } else {
                    if (warehouseId != null) {
            var wh = warehouseRepo.findById(warehouseId).orElse(null);
            item.setWarehouseName(wh != null ? wh.getName() : text(sourceKanban.getWarehouseName()));
        } else {
            item.setWarehouseName(text(sourceKanban.getWarehouseName()));
        }
        }


                if (locationId != null) {
            var loc = locationRepo.findById(locationId).orElse(null);
            if (loc != null) item.setLocationName(loc.getCode());
        } else {
                    if (locationId != null) {
            var loc = locationRepo.findById(locationId).orElse(null);
            item.setLocationName(loc != null ? loc.getCode() : text(sourceKanban.getLocationName()));
        } else {
            item.setLocationName(text(sourceKanban.getLocationName()));
        }
        }


                if (containerCode != null && !containerCode.isBlank()) {
            item.setContainerCode(containerCode);
            var con = containerRepo.findAll().stream().filter(c -> containerCode.equalsIgnoreCase(text(c.getCode()))).findFirst().orElse(null);
            if (con != null) item.setContainerName(con.getName());
        }
        item.setRepackKanbanNo(nextRepackKanbanNo());





        var supplier = supplierRepo.findAll().stream()


                .filter(s -> Objects.equals(s.getName(), sourceKanban.getSupplierName()))


                .findFirst().orElse(null);


        if (supplier != null) order.setSupplierId(supplier.getId());


        applyRepackRuleDefaults(item, order.getSupplierId());





        order.getItems().add(item);


        var saved = orderRepo.save(order);





        // Generate repack kanban


        RepackKanban rk = new RepackKanban();


        rk.setKanbanNo(item.getRepackKanbanNo());


        rk.setOrderId(saved.getId());


        rk.setOrderNo(orderNo);


        rk.setSourceKanbanNo(sourceKanban.getKanbanNo());


        rk.setSourceBusinessType("OUTBOUND");


        rk.setPartId(sourceKanban.getPartId());


        rk.setPartCode(sourceKanban.getPartCode());


        rk.setPartName(sourceKanban.getPartName());


        rk.setQty(qty);


        rk.setUnit(sourceKanban.getUnit());


        rk.setSupplierName(text(sourceKanban.getSupplierName()));


        rk.setTargetContainerCode(item.getContainerCode());


        rk.setTargetContainerName(item.getContainerName());


        rk.setTargetContainerType(item.getTargetContainerType());


        rk.setTargetPackageQty(item.getTargetPackageQty());


                    rk.setWarehouseName(item.getWarehouseName());


                    rk.setLocationName(item.getLocationName());


                    rk.setSourceWarehouseName(text(sourceKanban.getWarehouseName()));


                    rk.setSourceLocationName(text(sourceKanban.getLocationName()));


        rk.setStatus("PRINTED");


        rk.setPrintTime(LocalDateTime.now());


        rk.setOperator(operator);


        var savedKanban = repackKanbanRepo.save(rk);





        // Update source outbound kanban


        if (qty >= maxQty) {


            sourceKanban.setStatus("REPACKED");


            sourceKanban.setActualQty(0);


        } else {


            sourceKanban.setActualQty(maxQty - qty);


        }


        outboundKanbanRepo.save(sourceKanban);





        return ok("转包单已创建，已生成转包看板",


                Map.of("order", saved, "kanbans", List.of(savedKanban)));


    }





    private Map<String, Object> executeRepackTransfer(RepackKanban kanban, int actualQty, String operator, Long warehouseId, Long locationId, String containerCode) {


        if (!"PRINTED".equals(kanban.getStatus())) return fail("该转包看板已处理，不能重复扫描");





        int planQty = safe(kanban.getQty());


        if (actualQty > planQty) return fail("实际数量不能超过计划数量 " + planQty);





        int balanceQty = planQty - actualQty;


        String direction = text(kanban.getSourceBusinessType());





        // Update repack kanban


        kanban.setActualQty(actualQty);


        kanban.setBalanceQty(balanceQty);


        kanban.setRepackTime(LocalDateTime.now());


        kanban.setOperator(operator);





        // For source kanban that was SCANNED (???), need to deduct inventory first


        if ("INBOUND".equals(direction) || "SCANNED".equals(direction)) {


            // Source was from inventory - deduct and re-add


            var sourceKanban = kanbanRepo.findByKanbanNo(kanban.getSourceKanbanNo()).orElse(null);


            if (sourceKanban != null && "SCANNED".equals(sourceKanban.getStatus())) {


                // Deduct from source inventory


                inventoryRepo.save(inventoryRecord(sourceKanban, -actualQty, "REPACK_OUTBOUND", kanban.getOrderNo()));


            }


            // Add to target inventory


            kanban.setStatus("REPACK_INBOUND");


            inventoryRepo.save(inventoryRecord(kanban, actualQty, "REPACK_INBOUND", kanban.getOrderNo()));


        } else if ("OUTBOUND".equals(direction)) {


            // Outbound kanban being repacked


            kanban.setStatus("REPACK_OUTBOUND");


            inventoryRepo.save(inventoryRecord(kanban, -actualQty, "REPACK_OUTBOUND", kanban.getOrderNo()));


        } else {


            // Default: mark as completed


            kanban.setStatus(balanceQty > 0 ? "BALANCE" : "REPACK_INBOUND");


            inventoryRepo.save(inventoryRecord(kanban, actualQty, "REPACK_INBOUND", kanban.getOrderNo()));


        }





        repackKanbanRepo.save(kanban);





        // Update order status


        var order = orderRepo.findByOrderNo(kanban.getOrderNo()).orElse(null);


        if (order != null) {


            order.getItems().stream()


                    .filter(i -> Objects.equals(i.getRepackKanbanNo(), kanban.getKanbanNo()))


                    .findFirst()


                    .ifPresent(item -> {


                        item.setActualQty(safe(item.getActualQty()) + actualQty);


                    });


            updateOrderStatus(order);


            orderRepo.save(order);


        }





        return ok("转包执行成功，实际转包" + actualQty + "，结余" + balanceQty,


                Map.of("kanban", kanban, "balanceQty", balanceQty));


    }








    @GetMapping("/pending-kanbans")


    public Map<String, Object> pendingKanbans() {


        List<RepackKanban> list = repackKanbanRepo.findAll().stream()


                .filter(k -> "PRINTED".equals(k.getStatus()))


                .sorted(Comparator.comparing(RepackKanban::getPrintTime, Comparator.nullsLast(Comparator.reverseOrder())))


                .toList();


        return ok(list);


    }





    @GetMapping("/recent-records")


    public Map<String, Object> recentRecords() {


        List<RepackKanban> list = repackKanbanRepo.findAll().stream()


                .filter(k -> !"PRINTED".equals(k.getStatus()) && !"VOIDED".equals(k.getStatus()))


                .sorted(Comparator.comparing(RepackKanban::getRepackTime, Comparator.nullsLast(Comparator.reverseOrder())))


                .limit(50).toList();


        return ok(list);


    }








    @GetMapping("/lookup-kanban/{kanbanNo}")


    public Map<String, Object> lookupKanban(@PathVariable String kanbanNo) {


        // Check repack kanban


        var rk = repackKanbanRepo.findByKanbanNo(kanbanNo).orElse(null);


        if (rk != null) {


            Map<String, Object> result = new LinkedHashMap<>();


            result.put("type", "REPACK");


            result.put("data", rk);


            return ok(result);


        }


        // Check inbound kanban


        var ik = kanbanRepo.findByKanbanNo(kanbanNo).orElse(null);


        if (ik != null) {


            Map<String, Object> result = new LinkedHashMap<>();


            result.put("type", "INBOUND");


            result.put("data", ik);


            return ok(result);


        }


        // Check outbound kanban


        var okb = outboundKanbanRepo.findByKanbanNo(kanbanNo).orElse(null);


        if (okb != null) {


            Map<String, Object> result = new LinkedHashMap<>();


            result.put("type", "OUTBOUND");


            result.put("data", okb);


            return ok(result);


        }


        return fail("请输入转包看板号");


    }





    private List<Kanban> availableStockKanbans(Long partId) {


        return kanbanRepo.findByPartIdAndStatus(partId, "SCANNED").stream()


                .filter(k -> !Boolean.TRUE.equals(k.getSealed()))


                .filter(k -> safe(k.getQty()) > 0)


                .sorted(Comparator.comparing(Kanban::getScanTime, Comparator.nullsLast(Comparator.naturalOrder())))


                .toList();


    }

    private List<StockSource> availableStockSources(Long partId) {
        List<StockSource> sources = new ArrayList<>();
        for (var kanban : availableStockKanbans(partId)) {
            sources.add(new StockSource(kanban, null));
        }
        repackKanbanRepo.findAll().stream()
                .filter(k -> Objects.equals(k.getPartId(), partId))
                .filter(k -> "REPACK_INBOUND".equals(k.getStatus()))
                .filter(k -> safe(k.getQty()) > 0)
                .map(k -> new StockSource(null, k))
                .forEach(sources::add);
        sources.sort(Comparator
                .comparing(StockSource::time, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StockSource::kanbanNo));
        return sources;
    }





    private int availableStockQty(Long partId) {


        return availableStockSources(partId).stream().mapToInt(StockSource::qty).sum();


    }

    private record StockSource(Kanban kanban, RepackKanban repackKanban) {
        int qty() {
            return kanban != null ? (kanban.getQty() == null ? 0 : kanban.getQty()) : (repackKanban.getQty() == null ? 0 : repackKanban.getQty());
        }
        String kanbanNo() {
            return kanban != null ? kanban.getKanbanNo() : repackKanban.getKanbanNo();
        }
        LocalDateTime time() {
            if (kanban != null) return kanban.getScanTime();
            return repackKanban.getRepackTime() != null ? repackKanban.getRepackTime() : repackKanban.getPrintTime();
        }
        String warehouseName() {
            return kanban != null ? kanban.getWarehouseName() : repackKanban.getWarehouseName();
        }
        String locationName() {
            return kanban != null ? kanban.getLocationName() : repackKanban.getLocationName();
        }
    }





    private void applyRepackRuleDefaults(RepackOrderItem item, Long supplierId) {
        if (supplierId == null || item.getPartId() == null) return;
        var part = partRepo.findById(item.getPartId()).orElse(null);
        if (part == null) return;
        if (item.getOriginalPackageQty() == null || item.getOriginalPackageQty() <= 0) item.setOriginalPackageQty(part.getOriginalPackageQty());
        if (item.getTargetPackageQty() == null || item.getTargetPackageQty() <= 0) item.setTargetPackageQty(part.getTargetPackageQty());
        if (text(item.getTargetContainerType()).isBlank()) item.setTargetContainerType(part.getRepackContainerType());
        if (text(item.getContainerCode()).isBlank()) {
            findRecommendedContainer(part.getRepackContainerType(), item.getPartCode(), null, item.getTargetPackageQty()).ifPresent(container -> {
                item.setContainerCode(container.getCode());
                item.setContainerName(container.getName());
            });
        }
    }

    private Optional<Container> findRecommendedContainer(String containerType, String partCode, String supplierCode, Integer targetCapacity) {
        String normalizedType = text(containerType);
        if (normalizedType.isBlank()) return Optional.empty();
        List<Container> candidates = containerRepo.findAll().stream()
                .filter(c -> normalizedType.equalsIgnoreCase(text(c.getType())))
                .filter(c -> text(c.getPartCode()).isBlank() || text(c.getPartCode()).equalsIgnoreCase(text(partCode)))
                .filter(c -> text(c.getSupplierCode()).isBlank() || text(c.getSupplierCode()).equalsIgnoreCase(text(supplierCode)))
                .toList();
        if (targetCapacity != null && targetCapacity > 0) {
            Optional<Container> exact = candidates.stream()
                    .filter(c -> Objects.equals(c.getCapacity(), targetCapacity))
                    .findFirst();
            if (exact.isPresent()) return exact;
        }
        return candidates.stream().findFirst();
    }

    private int repackCapacity(RepackOrderItem item) {
        int ruleCapacity = safe(item.getTargetPackageQty());
        if (ruleCapacity > 0) return ruleCapacity;
        return containerCapacity(item.getContainerCode());
    }

    private int containerCapacity(String code) {


        String normalized = text(code);


        if (normalized.isBlank()) return 0;


        return containerRepo.findAll().stream()


                .filter(c -> normalized.equalsIgnoreCase(text(c.getCode())))


                .map(Container::getCapacity)


                .filter(Objects::nonNull)


                .findFirst()


                .orElse(0);


    }





    private RepackOrderItem copyItem(RepackOrderItem source) {


        RepackOrderItem target = new RepackOrderItem();


        target.setPartId(source.getPartId());


        target.setSourceKanbanNo(source.getSourceKanbanNo());


        target.setSourceBusinessType(source.getSourceBusinessType());


        target.setSourceContainerCode(source.getSourceContainerCode());


        target.setPartCode(source.getPartCode());


        target.setPartName(source.getPartName());


        target.setUnit(source.getUnit());


        target.setPlanQty(source.getPlanQty());


        target.setActualQty(source.getActualQty());


        target.setContainerCode(source.getContainerCode());


        target.setContainerName(source.getContainerName());


        target.setTargetContainerType(source.getTargetContainerType());


        target.setOriginalPackageQty(source.getOriginalPackageQty());


        target.setTargetPackageQty(source.getTargetPackageQty());


        target.setWarehouseId(source.getWarehouseId());


        target.setWarehouseName(source.getWarehouseName());


        target.setLocationId(source.getLocationId());


        target.setLocationName(source.getLocationName());


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





    @ExceptionHandler(IllegalArgumentException.class)


    public Map<String, Object> badRequest(IllegalArgumentException e) {


        return fail(e.getMessage());


    }





    private int safeInt(Object value) {


        if (value instanceof Number n) return n.intValue();


        try { return value == null ? 0 : Integer.parseInt(String.valueOf(value)); }


        catch (NumberFormatException e) { return 0; }


    }


    private void updateOrderStatus(RepackOrder order) {
        List<RepackKanban> kanbans = order.getId() == null ? List.of() : repackKanbanRepo.findByOrderId(order.getId());
        if (!kanbans.isEmpty()) {
            boolean hasPending = kanbans.stream().anyMatch(k -> "PRINTED".equals(k.getStatus()));
            boolean hasProcessed = kanbans.stream().anyMatch(k -> !"PRINTED".equals(k.getStatus()) && !"VOIDED".equals(k.getStatus()));
            order.setStatus(hasPending ? (hasProcessed ? "PROCESSING" : "PENDING") : "COMPLETED");
        } else {
            boolean allScanned = order.getItems().stream().allMatch(i -> safe(i.getActualQty()) >= safe(i.getPlanQty()));
            boolean anyScanned = order.getItems().stream().anyMatch(i -> safe(i.getActualQty()) > 0);
            order.setStatus(allScanned ? "COMPLETED" : anyScanned ? "PROCESSING" : "PENDING");
        }
        order.setUpdateTime(LocalDateTime.now());
    }


    private int safe(Integer value) { return value == null ? 0 : value; }


    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }


    private boolean isHandRepackOrder(RepackOrder order) {


        return order != null && !text(order.getSourceKanbanNo()).isBlank() && !text(order.getSourceBusinessType()).isBlank();


    }


    private boolean contains(String value, String keyword) {


        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT));


    }


    private Map<String, Object> fail(String message) { return Map.of("code", 400, "message", message); }


    private Map<String, Object> ok(Object data) { return Map.of("code", 200, "data", data); }


    private Map<String, Object> ok(String message, Object data) {


        return Map.of("code", 200, "message", message, "data", data);


    }


}


