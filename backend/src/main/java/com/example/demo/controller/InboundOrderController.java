package com.example.demo.controller;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/inbound")
public class InboundOrderController {

    private final InboundOrderRepository orderRepo;
    private final KanbanRepository kanbanRepo;
    private final InventoryRecordRepository inventoryRepo;
    private final KanbanController kanbanController;
    private final SupplierRepository supplierRepo;
    private final PartRepository partRepo;
    private final WarehouseRepository warehouseRepo;
    private final LocationRepository locationRepo;
    private final ContainerRepository containerRepo;
    private final OutboundKanbanRepository outboundKanbanRepo;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public InboundOrderController(InboundOrderRepository o, KanbanRepository k, KanbanController kc, InventoryRecordRepository i,
                                  SupplierRepository supplierRepo, PartRepository partRepo,
                                  WarehouseRepository warehouseRepo, LocationRepository locationRepo,
                                  ContainerRepository containerRepo, OutboundKanbanRepository outboundKanbanRepo) {
        this.orderRepo = o; this.kanbanRepo = k; this.kanbanController = kc; this.inventoryRepo = i;
        this.supplierRepo = supplierRepo; this.partRepo = partRepo; this.warehouseRepo = warehouseRepo;
        this.locationRepo = locationRepo; this.containerRepo = containerRepo;
        this.outboundKanbanRepo = outboundKanbanRepo;
    }

    @GetMapping("/orders")
    public Map<String, Object> listOrders(
            @RequestParam(defaultValue="") String supplier,
            @RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="") String inboundOrderNo,
            @RequestParam(defaultValue="") String orderNo) {
        List<InboundOrder> list = orderRepo.findAll();
        if(!status.isEmpty()) list = list.stream().filter(o->status.equals(o.getStatus())).collect(Collectors.toList());
        if(!supplier.isBlank()) list = list.stream()
                .filter(o->containsIgnoreCase(o.getSupplierName(), supplier)).collect(Collectors.toList());
        if(!inboundOrderNo.isBlank()) list = list.stream()
                .filter(o->containsIgnoreCase(o.getOrderNo(), inboundOrderNo)).collect(Collectors.toList());
        if(!orderNo.isBlank()) list = list.stream()
                .filter(o->containsIgnoreCase(o.getSourceOrderNo(), orderNo)).collect(Collectors.toList());
        list.sort((a,b)->b.getCreateTime().compareTo(a.getCreateTime()));
        return Map.of("code",200,"data",list);
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT));
    }

    private boolean containsAnyKanban(String values, Set<String> kanbanNos) {
        if(values == null || values.isBlank() || kanbanNos == null || kanbanNos.isEmpty()) return false;
        return Arrays.stream(values.split(","))
                .map(String::trim)
                .anyMatch(kanbanNos::contains);
    }

    private Supplier prepareOrder(InboundOrder order) {
        if(order.getSupplierId() == null) throw new IllegalArgumentException("请选择供应商");
        var supplier = supplierRepo.findById(order.getSupplierId())
                .orElseThrow(() -> new IllegalArgumentException("供应商不存在"));
        order.setSupplierName(supplier.getName());
        if(order.getItems() == null || order.getItems().isEmpty()) {
            throw new IllegalArgumentException("入库明细不能为空");
        }

        for(var item : order.getItems()) {
            if(item.getPartId() == null || item.getPlanQty() == null || item.getPlanQty() <= 0) {
                throw new IllegalArgumentException("入库明细不完整");
            }
            var part = partRepo.findById(item.getPartId())
                    .orElseThrow(() -> new IllegalArgumentException("零件不存在"));
            if(!Objects.equals(part.getSupplierId(), supplier.getId())) {
                throw new IllegalArgumentException("零件 " + part.getCode() + " 不属于所选供应商");
            }
            item.setPartCode(part.getCode());
            item.setPartName(part.getName());
            item.setSpec(part.getSpec());
            item.setUnit(part.getUnit());

            if(item.getWarehouseId() != null) {
                var warehouse = warehouseRepo.findById(item.getWarehouseId())
                        .orElseThrow(() -> new IllegalArgumentException("仓库不存在"));
                item.setWarehouseName(warehouse.getName());
            }
            if(item.getLocationId() != null) {
                var location = locationRepo.findById(item.getLocationId())
                        .orElseThrow(() -> new IllegalArgumentException("库位不存在"));
                if(item.getWarehouseId() != null && !Objects.equals(location.getWarehouseId(), item.getWarehouseId())) {
                    throw new IllegalArgumentException("库位 " + location.getCode() + " 不属于所选仓库");
                }
                item.setLocationName(location.getCode());
            }
            if(item.getContainerId() != null) {
                var container = containerRepo.findById(item.getContainerId())
                        .orElseThrow(() -> new IllegalArgumentException("器具不存在"));
                validateContainerBinding(container, supplier, part);
                item.setContainerCode(container.getCode());
                item.setContainerName(container.getName());
            }
        }
        return supplier;
    }

    private void validateContainerBinding(Container container, Supplier supplier, Part part) {
        if(container.getSupplierCode() != null && !container.getSupplierCode().isBlank()
                && supplier.getCode() != null && !container.getSupplierCode().equalsIgnoreCase(supplier.getCode())) {
            throw new IllegalArgumentException("器具 " + container.getCode() + " 不属于当前供应商");
        }
        if(container.getPartCode() != null && !container.getPartCode().isBlank()
                && !container.getPartCode().equalsIgnoreCase(part.getCode())) {
            throw new IllegalArgumentException("器具 " + container.getCode() + " 不适用于零件 " + part.getCode());
        }
    }

    @GetMapping("/supplier-storage/{supplierId}")
    public Map<String, Object> supplierStorage(@PathVariable Long supplierId) {
        var supplier = supplierRepo.findById(supplierId).orElse(null);
        if(supplier == null) return Map.of("code",404,"message","供应商不存在");

        List<InboundOrder> orders = orderRepo.findAll().stream()
                .filter(order -> Objects.equals(order.getSupplierId(), supplierId))
                .sorted((a,b) -> {
                    LocalDateTime at = a.getCreateTime() == null ? LocalDateTime.MIN : a.getCreateTime();
                    LocalDateTime bt = b.getCreateTime() == null ? LocalDateTime.MIN : b.getCreateTime();
                    return bt.compareTo(at);
                })
                .toList();

        Long recommendedWarehouseId = supplier.getPreferredWarehouseId();
        String recommendedWarehouseName = supplier.getPreferredWarehouseName();
        if(recommendedWarehouseId == null) {
            for(var order : orders) {
                if(order.getWarehouseId() != null) {
                    recommendedWarehouseId = order.getWarehouseId();
                    recommendedWarehouseName = order.getWarehouseName();
                    break;
                }
                if(order.getItems() == null) continue;
                var item = order.getItems().stream().filter(i -> i.getWarehouseId() != null).findFirst().orElse(null);
                if(item != null) {
                    recommendedWarehouseId = item.getWarehouseId();
                    recommendedWarehouseName = item.getWarehouseName();
                    break;
                }
            }
        }
        if(recommendedWarehouseId == null) {
            var warehouse = warehouseRepo.findAll().stream().findFirst().orElse(null);
            if(warehouse != null) {
                recommendedWarehouseId = warehouse.getId();
                recommendedWarehouseName = warehouse.getName();
            }
        } else {
            var warehouse = warehouseRepo.findById(recommendedWarehouseId).orElse(null);
            if(warehouse != null) recommendedWarehouseName = warehouse.getName();
        }

        Long recommendedLocationId = null;
        String recommendedLocationName = "";
        if(recommendedWarehouseId != null) {
            var location = locationRepo.findByWarehouseId(recommendedWarehouseId).stream().findFirst().orElse(null);
            if(location != null) {
                recommendedLocationId = location.getId();
                recommendedLocationName = location.getCode();
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("supplierId", supplier.getId());
        data.put("supplierName", supplier.getName());
        data.put("hasInboundOrders", !orders.isEmpty());
        data.put("preferredWarehouseId", supplier.getPreferredWarehouseId());
        data.put("preferredWarehouseName", supplier.getPreferredWarehouseName());
        data.put("recommendedWarehouseId", recommendedWarehouseId);
        data.put("recommendedWarehouseName", recommendedWarehouseName == null ? "" : recommendedWarehouseName);
        data.put("recommendedLocationId", recommendedLocationId);
        data.put("recommendedLocationName", recommendedLocationName);
        return Map.of("code",200,"data",data);
    }

    private void applySupplierWarehousePreference(InboundOrder order, Supplier supplier) {
        if(order.getItems() == null || supplier == null) return;
        Long warehouseId = order.getWarehouseId();
        if(warehouseId == null) {
            var item = order.getItems().stream().filter(i -> i.getWarehouseId() != null).findFirst().orElse(null);
            if(item != null) warehouseId = item.getWarehouseId();
        }
        if(warehouseId == null) return;
        var warehouse = warehouseRepo.findById(warehouseId).orElse(null);
        if(warehouse == null) return;
        order.setWarehouseId(warehouse.getId());
        order.setWarehouseName(warehouse.getName());
        supplier.setPreferredWarehouseId(warehouse.getId());
        supplier.setPreferredWarehouseName(warehouse.getName());
        supplierRepo.save(supplier);
    }

    @GetMapping("/order/{id}")
    public Map<String, Object> getOrder(@PathVariable Long id) {
        return orderRepo.findById(id).map(o->Map.of("code",200,"data",(Object)o)).orElse(Map.of("code",404,"message","入库单不存在"));
    }

    @PostMapping("/order")
    public Map<String, Object> createOrder(@RequestBody InboundOrder order) {
        order.setId(null); order.setOrderNo("RK"+LocalDateTime.now().format(FMT));
        if(order.getInboundType() == null || order.getInboundType().isBlank()) order.setInboundType("正常入库");
        order.setManualInbound(false);
        order.setStatus("DRAFT"); order.setCreateTime(LocalDateTime.now()); order.setUpdateTime(LocalDateTime.now());
        if(order.getItems()!=null) order.getItems().forEach(i->{i.setId(null);i.setActualQty(0);});
        try {
            var supplier = prepareOrder(order);
            applySupplierWarehousePreference(order, supplier);
        } catch (IllegalArgumentException e) {
            return Map.of("code",400,"message",e.getMessage());
        }
        var saved = orderRepo.save(order);
        // 自动生成看板
        int count = kanbanController.buildKanbans(saved).size();
        if(count > 0) {
            saved.setStatus("CONFIRMED");
            orderRepo.save(saved);
        }
        return Map.of("code",200,"message","入库单创建成功，已自动生成"+count+"张看板","data",saved);
    }

    @PutMapping("/order")
    @Transactional
    public Map<String, Object> updateOrder(@RequestBody InboundOrder order) {
        var exist = orderRepo.findById(order.getId()).orElse(null);
        if(exist==null) return Map.of("code",404,"message","入库单不存在");
        if("COMPLETED".equals(exist.getStatus())) return Map.of("code",400,"message","已完成的入库单不可修改");
        if("VOIDED".equals(exist.getStatus())) return Map.of("code",400,"message","已作废的入库单不可修改");
        if(order.getInboundType() == null || order.getInboundType().isBlank()) order.setInboundType("正常入库");

        List<InboundOrderItem> oldItems = exist.getItems()!=null ? new ArrayList<>(exist.getItems()) : List.of();
        List<InboundOrderItem> newItems = order.getItems()!=null ? order.getItems() : List.of();

        Map<Long, InboundOrderItem> oldMap = oldItems.stream()
                .collect(Collectors.toMap(InboundOrderItem::getPartId, i->i, (a,b)->a));
        Map<Long, InboundOrderItem> newMap = newItems.stream()
                .collect(Collectors.toMap(InboundOrderItem::getPartId, i->i, (a,b)->a));

        // 回填已入库数量
        for(var oldItem : oldItems) {
            var newItem = newMap.get(oldItem.getPartId());
            if(newItem != null && oldItem.getActualQty()!=null && oldItem.getActualQty()>0)
                newItem.setActualQty(oldItem.getActualQty());
        }

        order.setUpdateTime(LocalDateTime.now()); order.setCreateTime(exist.getCreateTime()); order.setOrderNo(exist.getOrderNo());
        order.setStatus("DRAFT"); // 先设草稿，后面根据看板更新
        if(order.getItems()!=null) order.getItems().forEach(i->{
            if(i.getId()==null) i.setId(null);
            i.setActualQty(i.getActualQty()!=null?i.getActualQty():0);
        });
        try {
            prepareOrder(order);
        } catch (IllegalArgumentException e) {
            return Map.of("code",400,"message",e.getMessage());
        }

        var saved = orderRepo.save(order);

        // 清除旧看板（已入库、已转包等已处理看板保留）
        var oldKanbans = kanbanRepo.findByOrderId(order.getId());
        int cleared = 0;
        for(var kb : oldKanbans) {
            if(!isHandledKanban(kb.getStatus())) {
                kanbanRepo.delete(kb);
                cleared++;
            }
        }

        // 自动生成新看板
        int generated = kanbanController.buildKanbans(saved).size();
        var remainingKanbans = kanbanRepo.findByOrderId(order.getId());
        boolean hasHandled = remainingKanbans.stream().anyMatch(k->isHandledKanban(k.getStatus()));
        boolean hasPrinted = remainingKanbans.stream().anyMatch(k->"PRINTED".equals(k.getStatus()));

        if(hasHandled && !hasPrinted) saved.setStatus("COMPLETED");
        else if(hasHandled) saved.setStatus("PARTIAL");
        else if(hasPrinted) saved.setStatus("CONFIRMED");
        else saved.setStatus("DRAFT");
        orderRepo.save(saved);

        String msg = String.format("入库单修改成功，%d张看板已更新，%d张新看板已生成", cleared, generated);
        return Map.of("code",200,"message",msg,"data",saved);
    }

    private boolean eq(Object a, Object b) {
        if(a==null && b==null) return true;
        if(a==null || b==null) return false;
        return a.equals(b);
    }

    private boolean isHandledKanban(String status) {
        return "SCANNED".equals(status) || "REPACKED".equals(status) || "OUTBOUND".equals(status);
    }

    @DeleteMapping("/order/{id}")
    @Transactional
    public Map<String, Object> deleteOrder(@PathVariable Long id) {
        var order = orderRepo.findById(id).orElse(null);
        if(order==null) return Map.of("code",404,"message","入库单不存在");
        if("VOIDED".equals(order.getStatus())) return Map.of("code",400,"message","已作废的入库单不能删除");
        // 按入库单号删除扫码入库和手工入库产生的全部库存记录
        var kanbans = kanbanRepo.findByOrderId(id);
        Set<String> kanbanNos = kanbans.stream().map(Kanban::getKanbanNo).collect(Collectors.toSet());
        boolean linkedOutbound = kanbans.stream().anyMatch(k -> "OUTBOUND".equals(k.getStatus()) || k.getOutboundOrderNo() != null)
                || outboundKanbanRepo.findAll().stream()
                .anyMatch(k -> containsAnyKanban(k.getSourceKanbanNo(), kanbanNos));
        if(linkedOutbound) return Map.of("code",400,"message","该入库单已有看板被出库单引用，不能直接删除；如需清理，请先处理关联出库单和库存流水");
        inventoryRepo.deleteAll(inventoryRepo.findByRefOrderNo(order.getOrderNo()));
        // 删除看板
        kanbanRepo.deleteAll(kanbans);
        // 删除入库单
        orderRepo.deleteById(id);
        return Map.of("code",200,"message","入库单及相关看板、库存记录已全部删除");
    }

    @PutMapping("/order/{id}/void")
    @Transactional
    public Map<String, Object> voidOrder(@PathVariable Long id) {
        var order = orderRepo.findById(id).orElse(null);
        if(order==null) return Map.of("code",404,"message","入库单不存在");
        if("COMPLETED".equals(order.getStatus())) return Map.of("code",400,"message","已完成的入库单不能作废");
        var kanbans = kanbanRepo.findByOrderId(id);
        for(var kb : kanbans) {
            if(!"SCANNED".equals(kb.getStatus())) kb.setStatus("VOIDED");
            kanbanRepo.save(kb);
        }
        order.setStatus("VOIDED"); order.setUpdateTime(LocalDateTime.now());
        orderRepo.save(order);
        return Map.of("code",200,"message","入库单已作废");
    }

    @PutMapping("/order/{id}/manual-inbound")
    @Transactional
    public Map<String, Object> manualInbound(@PathVariable Long id) {
        var order = orderRepo.findById(id).orElse(null);
        if(order==null) return Map.of("code",404,"message","入库单不存在");
        if("VOIDED".equals(order.getStatus())) return Map.of("code",400,"message","已作废的入库单不能手工入库");
        if("COMPLETED".equals(order.getStatus())) return Map.of("code",400,"message","该入库单已完成");

        var kanbans = kanbanRepo.findByOrderId(id);
        kanbanRepo.deleteAll(kanbans.stream().filter(k->!"SCANNED".equals(k.getStatus())).toList());

        int recordCount = 0;
        int totalQty = 0;
        for(var item : order.getItems()) {
            int actual = item.getActualQty()==null ? 0 : item.getActualQty();
            int remaining = item.getPlanQty()-actual;
            if(remaining<=0) continue;

            var inv = new InventoryRecord();
            inv.setPartId(item.getPartId()); inv.setPartCode(item.getPartCode()); inv.setPartName(item.getPartName());
            inv.setUnit(item.getUnit()); inv.setKanbanNo("MANUAL-"+order.getOrderNo()+"-"+item.getId());
            inv.setLocationId(item.getLocationId()); inv.setLocationName(item.getLocationName());
            inv.setQty(remaining); inv.setType("MANUAL_INBOUND"); inv.setRefOrderNo(order.getOrderNo());
            inv.setCreateTime(LocalDateTime.now());
            inventoryRepo.save(inv);

            item.setActualQty(item.getPlanQty());
            recordCount++;
            totalQty += remaining;
        }

        order.setManualInbound(true);
        order.setStatus("COMPLETED");
        order.setUpdateTime(LocalDateTime.now());
        orderRepo.save(order);
        return Map.of("code",200,"message","手工入库完成，共处理"+recordCount+"项、"+totalQty+"件","data",order);
    }

    @GetMapping("/order/{id}/kanbans")
    public Map<String, Object> getOrderKanbans(@PathVariable Long id) {
        return Map.of("code",200,"data",kanbanRepo.findByOrderId(id));
    }
}
