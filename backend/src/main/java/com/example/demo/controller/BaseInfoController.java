package com.example.demo.controller;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/baseinfo")
public class BaseInfoController {

    private final SupplierRepository supplierRepo;
    private final PartRepository partRepo;
    private final WarehouseRepository warehouseRepo;
    private final LocationRepository locationRepo;
    private final CustomerRepository customerRepo;
    private final ContainerRepository containerRepo;
    private final InventoryRecordRepository inventoryRepo;

    public BaseInfoController(SupplierRepository supplierRepo,
                              PartRepository partRepo,
                              WarehouseRepository warehouseRepo,
                              LocationRepository locationRepo,
                              CustomerRepository customerRepo,
                              ContainerRepository containerRepo,
                              InventoryRecordRepository inventoryRepo) {
        this.supplierRepo = supplierRepo;
        this.partRepo = partRepo;
        this.warehouseRepo = warehouseRepo;
        this.locationRepo = locationRepo;
        this.customerRepo = customerRepo;
        this.containerRepo = containerRepo;
        this.inventoryRepo = inventoryRepo;
    }

    @GetMapping("/suppliers")
    public Map<String, Object> listSuppliers(@RequestParam(defaultValue = "") String code,
                                             @RequestParam(defaultValue = "") String name,
                                             @RequestParam(defaultValue = "") String address,
                                             @RequestParam(defaultValue = "") String phone,
                                             @RequestParam(defaultValue = "") String landline,
                                             @RequestParam(defaultValue = "") String contact,
                                             @RequestParam(defaultValue = "") String level) {
        var list = supplierRepo.findAll().stream()
                .filter(item -> contains(item.getCode(), code))
                .filter(item -> contains(item.getName(), name))
                .filter(item -> contains(item.getAddress(), address))
                .filter(item -> contains(item.getPhone(), phone))
                .filter(item -> contains(item.getLandline(), landline))
                .filter(item -> contains(item.getContact(), contact))
                .filter(item -> contains(item.getLevel(), level))
                .toList();
        return ok(list);
    }

    @PostMapping("/supplier")
    public Map<String, Object> addSupplier(@RequestBody Supplier supplier) {
        supplier.setId(null);
        fillSupplier(supplier);
        ensureSupplierCodeUnique(supplier);
        return ok(supplierRepo.save(supplier));
    }

    @PutMapping("/supplier")
    public Map<String, Object> updateSupplier(@RequestBody Supplier supplier) {
        if (supplier.getId() == null || supplierRepo.findById(supplier.getId()).isEmpty()) return error(404, "供应商不存在");
        fillSupplier(supplier);
        ensureSupplierCodeUnique(supplier);
        return ok(supplierRepo.save(supplier));
    }

    @DeleteMapping("/supplier/{id}")
    public Map<String, Object> deleteSupplier(@PathVariable Long id) {
        supplierRepo.deleteById(id);
        return message("供应商已删除");
    }

    @PostMapping("/suppliers/batch")
    public Map<String, Object> batchAddSuppliers(@RequestBody List<Supplier> suppliers) {
        int count = 0;
        List<String> errors = new ArrayList<>();
        for (Supplier supplier : suppliers) {
            try {
                supplier.setId(null);
                fillSupplier(supplier);
                ensureSupplierCodeUnique(supplier);
                supplierRepo.save(supplier);
                count++;
            } catch (Exception e) {
                errors.add(label(supplier.getCode(), supplier.getName()) + "：" + e.getMessage());
            }
        }
        return batchResult("供应商", count, errors);
    }

    @GetMapping("/parts")
    public Map<String, Object> listParts(@RequestParam(required = false) Long supplierId,
                                         @RequestParam(defaultValue = "") String code,
                                         @RequestParam(defaultValue = "") String name,
                                         @RequestParam(defaultValue = "") String supplier) {
        var stream = supplierId == null ? partRepo.findAll().stream() : partRepo.findBySupplierId(supplierId).stream();
        return ok(stream
                .filter(item -> contains(item.getCode(), code))
                .filter(item -> contains(item.getName(), name))
                .filter(item -> contains(item.getSupplierName(), supplier))
                .toList());
    }

    @PostMapping("/part")
    public Map<String, Object> addPart(@RequestBody Part part) {
        part.setId(null);
        fillPart(part);
        ensurePartCodeUnique(part);
        return ok(partRepo.save(part));
    }

    @PutMapping("/part")
    public Map<String, Object> updatePart(@RequestBody Part part) {
        if (part.getId() == null || partRepo.findById(part.getId()).isEmpty()) return error(404, "零件不存在");
        fillPart(part);
        ensurePartCodeUnique(part);
        return ok(partRepo.save(part));
    }

    @DeleteMapping("/part/{id}")
    public Map<String, Object> deletePart(@PathVariable Long id) {
        partRepo.deleteById(id);
        return message("零件已删除");
    }

    @PostMapping("/parts/batch")
    public Map<String, Object> batchAddParts(@RequestBody List<Part> parts) {
        int count = 0;
        List<String> errors = new ArrayList<>();
        for (Part part : parts) {
            try {
                part.setId(null);
                fillPart(part);
                ensurePartCodeUnique(part);
                partRepo.save(part);
                count++;
            } catch (Exception e) {
                errors.add(label(part.getCode(), part.getName()) + "：" + e.getMessage());
            }
        }
        return batchResult("零件", count, errors);
    }

    @GetMapping("/warehouses")
    public Map<String, Object> listWarehouses() {
        return ok(warehouseRepo.findAll().stream().map(this::fillWarehouseCapacityStats).toList());
    }

    @PostMapping("/warehouse")
    public Map<String, Object> addWarehouse(@RequestBody Warehouse warehouse) {
        warehouse.setId(null);
        fillWarehouse(warehouse);
        ensureWarehouseCodeUnique(warehouse);
        return ok(fillWarehouseCapacityStats(warehouseRepo.save(warehouse)));
    }

    @PutMapping("/warehouse")
    public Map<String, Object> updateWarehouse(@RequestBody Warehouse warehouse) {
        if (warehouse.getId() == null || warehouseRepo.findById(warehouse.getId()).isEmpty()) return error(404, "仓库不存在");
        fillWarehouse(warehouse);
        ensureWarehouseCodeUnique(warehouse);
        return ok(fillWarehouseCapacityStats(warehouseRepo.save(warehouse)));
    }

    @DeleteMapping("/warehouse/{id}")
    public Map<String, Object> deleteWarehouse(@PathVariable Long id) {
        warehouseRepo.deleteById(id);
        return message("仓库已删除");
    }

    @PostMapping("/warehouses/batch")
    public Map<String, Object> batchAddWarehouses(@RequestBody List<Warehouse> warehouses) {
        int count = 0;
        List<String> errors = new ArrayList<>();
        for (Warehouse warehouse : warehouses) {
            try {
                warehouse.setId(null);
                fillWarehouse(warehouse);
                ensureWarehouseCodeUnique(warehouse);
                warehouseRepo.save(warehouse);
                count++;
            } catch (Exception e) {
                errors.add(label(warehouse.getCode(), warehouse.getName()) + "：" + e.getMessage());
            }
        }
        return batchResult("仓库", count, errors);
    }

    @GetMapping("/locations")
    public Map<String, Object> listLocations(@RequestParam(required = false) Long warehouseId) {
        return ok(warehouseId == null ? locationRepo.findAll() : locationRepo.findByWarehouseId(warehouseId));
    }

    @PostMapping("/location")
    public Map<String, Object> addLocation(@RequestBody Location location) {
        location.setId(null);
        fillLocation(location);
        if (locationRepo.existsByCodeIgnoreCase(location.getCode())) return error(400, "库位编号已存在");
        Location saved = locationRepo.save(location);
        return locationResult(saved, List.of(saved.getWarehouseId()));
    }

    @PutMapping("/location")
    public Map<String, Object> updateLocation(@RequestBody Location location) {
        if (location.getId() == null || locationRepo.findById(location.getId()).isEmpty()) return error(404, "库位不存在");
        fillLocation(location);
        Location saved = locationRepo.save(location);
        return locationResult(saved, List.of(saved.getWarehouseId()));
    }

    @DeleteMapping("/location/{id}")
    public Map<String, Object> deleteLocation(@PathVariable Long id) {
        locationRepo.deleteById(id);
        return message("库位已删除");
    }

    @PostMapping("/locations/batch")
    @Transactional
    public Map<String, Object> batchAddLocations(@RequestBody Map<String, Object> body) {
        Long warehouseId = toLong(body.get("warehouseId"));
        int count = toInt(body.get("count"));
        int capacity = toInt(body.get("capacity"));
        if (warehouseId == null) return error(400, "请选择仓库");
        if (count <= 0) return error(400, "新增数量必须大于 0");
        if (capacity < 0) return error(400, "库位容量不能小于 0");
        Warehouse warehouse = warehouseRepo.findById(warehouseId).orElse(null);
        if (warehouse == null) return error(404, "仓库不存在");
        List<Location> saved = new ArrayList<>();
        int start = locationRepo.findByWarehouseId(warehouseId).size() + 1;
        for (int i = 0; i < count; i++) {
            String code;
            do {
                code = warehouse.getCode() + "-" + String.format("%03d", start++);
            } while (locationRepo.existsByCodeIgnoreCase(code));
            saved.add(locationRepo.save(new Location(code, code, warehouse.getId(), warehouse.getName(), capacity)));
        }
        return locationResult("批量新增库位成功", saved, List.of(warehouseId));
    }

    @GetMapping("/customers")
    public Map<String, Object> listCustomers(@RequestParam(defaultValue = "") String code,
                                             @RequestParam(defaultValue = "") String name,
                                             @RequestParam(defaultValue = "") String address,
                                             @RequestParam(defaultValue = "") String phone,
                                             @RequestParam(defaultValue = "") String landline,
                                             @RequestParam(defaultValue = "") String contact,
                                             @RequestParam(defaultValue = "") String level) {
        return ok(customerRepo.findAll().stream()
                .filter(item -> contains(item.getCode(), code))
                .filter(item -> contains(item.getName(), name))
                .filter(item -> contains(item.getAddress(), address))
                .filter(item -> contains(item.getPhone(), phone))
                .filter(item -> contains(item.getLandline(), landline))
                .filter(item -> contains(item.getContact(), contact))
                .filter(item -> contains(item.getLevel(), level))
                .toList());
    }

    @PostMapping("/customer")
    public Map<String, Object> addCustomer(@RequestBody Customer customer) {
        customer.setId(null);
        fillCustomer(customer);
        ensureCustomerCodeUnique(customer);
        return ok(customerRepo.save(customer));
    }

    @PutMapping("/customer")
    public Map<String, Object> updateCustomer(@RequestBody Customer customer) {
        if (customer.getId() == null || customerRepo.findById(customer.getId()).isEmpty()) return error(404, "客户不存在");
        fillCustomer(customer);
        ensureCustomerCodeUnique(customer);
        return ok(customerRepo.save(customer));
    }

    @DeleteMapping("/customer/{id}")
    public Map<String, Object> deleteCustomer(@PathVariable Long id) {
        customerRepo.deleteById(id);
        return message("客户已删除");
    }

    @PostMapping("/customers/batch")
    public Map<String, Object> batchAddCustomers(@RequestBody List<Customer> customers) {
        int count = 0;
        List<String> errors = new ArrayList<>();
        for (Customer customer : customers) {
            try {
                customer.setId(null);
                fillCustomer(customer);
                ensureCustomerCodeUnique(customer);
                customerRepo.save(customer);
                count++;
            } catch (Exception e) {
                errors.add(label(customer.getCode(), customer.getName()) + "：" + e.getMessage());
            }
        }
        return batchResult("客户", count, errors);
    }

    @GetMapping("/containers")
    public Map<String, Object> listContainers(@RequestParam(defaultValue = "") String type,
                                              @RequestParam(defaultValue = "") String partCode,
                                              @RequestParam(defaultValue = "") String supplierCode,
                                              @RequestParam(defaultValue = "") String vehicleModel,
                                              @RequestParam(defaultValue = "") String code) {
        return ok(containerRepo.findAll().stream()
                .filter(item -> contains(item.getType(), type))
                .filter(item -> contains(item.getPartCode(), partCode))
                .filter(item -> contains(item.getSupplierCode(), supplierCode))
                .filter(item -> contains(item.getVehicleModel(), vehicleModel))
                .filter(item -> contains(item.getCode(), code))
                .toList());
    }

    @PostMapping("/container")
    public Map<String, Object> addContainer(@RequestBody Container container) {
        container.setId(null);
        fillContainer(container);
        ensureContainerCodeUnique(container);
        return ok(containerRepo.save(container));
    }

    @PutMapping("/container")
    public Map<String, Object> updateContainer(@RequestBody Container container) {
        if (container.getId() == null || containerRepo.findById(container.getId()).isEmpty()) return error(404, "器具不存在");
        fillContainer(container);
        ensureContainerCodeUnique(container);
        return ok(containerRepo.save(container));
    }

    @DeleteMapping("/container/{id}")
    public Map<String, Object> deleteContainer(@PathVariable Long id) {
        containerRepo.deleteById(id);
        return message("器具已删除");
    }

    @PostMapping("/containers/batch")
    public Map<String, Object> batchAddContainers(@RequestBody List<Container> containers) {
        int count = 0;
        List<String> errors = new ArrayList<>();
        for (Container container : containers) {
            try {
                container.setId(null);
                fillContainer(container);
                ensureContainerCodeUnique(container);
                containerRepo.save(container);
                count++;
            } catch (Exception e) {
                errors.add(label(container.getCode(), container.getName()) + "：" + e.getMessage());
            }
        }
        return batchResult("器具", count, errors);
    }

    private void fillSupplier(Supplier supplier) {
        if (isBlank(supplier.getCode())) throw new IllegalArgumentException("请填写供应商编号");
        if (isBlank(supplier.getName())) throw new IllegalArgumentException("请填写供应商名称");
        supplier.setCode(trim(supplier.getCode()));
        supplier.setName(trim(supplier.getName()));
        supplier.setContact(trim(supplier.getContact()));
        supplier.setPhone(trim(supplier.getPhone()));
        supplier.setLandline(trim(supplier.getLandline()));
        supplier.setLevel(trim(supplier.getLevel()));
        supplier.setAddress(trim(supplier.getAddress()));
        fillSupplierWarehouse(supplier);
        if (supplier.getCreateTime() == null) supplier.setCreateTime(LocalDateTime.now());
    }

    private void fillPart(Part part) {
        if (isBlank(part.getCode())) throw new IllegalArgumentException("请填写零件编号");
        if (isBlank(part.getName())) throw new IllegalArgumentException("请填写零件名称");
        if (isBlank(part.getUnit())) throw new IllegalArgumentException("请填写单位");
        if (part.getSupplierId() == null) throw new IllegalArgumentException("请选择供应商");
        Supplier supplier = supplierRepo.findById(part.getSupplierId()).orElseThrow(() -> new IllegalArgumentException("供应商不存在"));
        if (part.getLowStock() == null) part.setLowStock(50);
        if (part.getHighStock() == null) part.setHighStock(0);
        if (part.getOriginalPackageQty() == null || part.getOriginalPackageQty() <= 0) throw new IllegalArgumentException("请填写大于 0 的原包装容量");
        if (part.getTargetPackageQty() == null || part.getTargetPackageQty() <= 0) throw new IllegalArgumentException("请填写大于 0 的转包容量");
        if (part.getTargetPackageQty() > part.getOriginalPackageQty()) throw new IllegalArgumentException("转包容量不能大于原包装容量");
        if (isBlank(part.getRepackContainerType())) throw new IllegalArgumentException("请填写转包器具类型");
        part.setCode(trim(part.getCode()));
        part.setName(trim(part.getName()));
        part.setSpec(trim(part.getSpec()));
        part.setUnit(trim(part.getUnit()));
        part.setSupplierName(supplier.getName());
        part.setCustomerBarcode(trim(part.getCustomerBarcode()));
        part.setRepackContainerType(trim(part.getRepackContainerType()));
        if (part.getCreateTime() == null) part.setCreateTime(LocalDateTime.now());
    }

    private void fillWarehouse(Warehouse warehouse) {
        if (isBlank(warehouse.getCode())) throw new IllegalArgumentException("请填写仓库编号");
        if (isBlank(warehouse.getName())) throw new IllegalArgumentException("请填写仓库名称");
        if (warehouse.getCapacity() == null) warehouse.setCapacity(0);
        if (warehouse.getCapacity() < 0) throw new IllegalArgumentException("仓库总容量不能小于 0");
        warehouse.setCode(trim(warehouse.getCode()));
        warehouse.setName(trim(warehouse.getName()));
        warehouse.setArea(trim(warehouse.getArea()));
    }

    private void fillLocation(Location location) {
        if (location.getWarehouseId() == null) throw new IllegalArgumentException("请选择仓库");
        if (isBlank(location.getCode())) throw new IllegalArgumentException("请填写库位编号");
        if (isBlank(location.getName())) throw new IllegalArgumentException("请填写库位名称");
        if (location.getCapacity() == null) location.setCapacity(0);
        if (location.getCapacity() < 0) throw new IllegalArgumentException("库位容量不能小于 0");
        Warehouse warehouse = warehouseRepo.findById(location.getWarehouseId()).orElseThrow(() -> new IllegalArgumentException("仓库不存在"));
        location.setCode(trim(location.getCode()));
        location.setName(trim(location.getName()));
        location.setWarehouseName(warehouse.getName());
    }

    private void fillCustomer(Customer customer) {
        if (isBlank(customer.getCode())) throw new IllegalArgumentException("请填写客户编号");
        if (isBlank(customer.getName())) throw new IllegalArgumentException("请填写客户名称");
        customer.setCode(trim(customer.getCode()));
        customer.setName(trim(customer.getName()));
        customer.setContact(trim(customer.getContact()));
        customer.setPhone(trim(customer.getPhone()));
        customer.setLandline(trim(customer.getLandline()));
        customer.setLevel(trim(customer.getLevel()));
        customer.setAddress(trim(customer.getAddress()));
        if (customer.getCreateTime() == null) customer.setCreateTime(LocalDateTime.now());
    }

    private void fillContainer(Container container) {
        if (isBlank(container.getCode())) throw new IllegalArgumentException("请填写器具编号");
        if (isBlank(container.getName())) throw new IllegalArgumentException("请填写器具名称");
        if (isBlank(container.getType())) throw new IllegalArgumentException("请填写器具类型");
        if (container.getCapacity() == null || container.getCapacity() <= 0) throw new IllegalArgumentException("器具容量必须大于 0");
        container.setCode(trim(container.getCode()));
        container.setName(trim(container.getName()));
        container.setType(trim(container.getType()));
        container.setSpec(trim(container.getSpec()));
        container.setSupplierCode(trim(container.getSupplierCode()));
        container.setPartCode(trim(container.getPartCode()));
        container.setVehicleModel(trim(container.getVehicleModel()));
        if (container.getCreateTime() == null) container.setCreateTime(LocalDateTime.now());
    }

    private void fillSupplierWarehouse(Supplier supplier) {
        if (supplier.getPreferredWarehouseId() == null) {
            supplier.setPreferredWarehouseName("");
            return;
        }
        Warehouse warehouse = warehouseRepo.findById(supplier.getPreferredWarehouseId()).orElse(null);
        supplier.setPreferredWarehouseName(warehouse == null ? "" : warehouse.getName());
    }

    private Warehouse fillWarehouseCapacityStats(Warehouse warehouse) {
        int locationTotal = warehouse.getId() == null ? 0 : locationCapacity(warehouse.getId());
        int stockUsed = warehouse.getId() == null ? 0 : warehouseStockQty(warehouse.getId());
        int total = warehouse.getCapacity() == null ? 0 : warehouse.getCapacity();
        warehouse.setLocationCapacity(locationTotal);
        warehouse.setRemainingCapacity(total - stockUsed);
        warehouse.setOverCapacity(total > 0 && stockUsed > total);
        return warehouse;
    }

    private int locationCapacity(Long warehouseId) {
        if (warehouseId == null) return 0;
        return locationRepo.findByWarehouseId(warehouseId).stream()
                .map(Location::getCapacity)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private int warehouseStockQty(Long warehouseId) {
        Map<Long, Long> locationWarehouse = locationRepo.findAll().stream()
                .filter(location -> location.getId() != null && location.getWarehouseId() != null)
                .collect(Collectors.toMap(Location::getId, Location::getWarehouseId, (a, b) -> a));
        Map<String, Long> locationCodeWarehouse = locationRepo.findAll().stream()
                .filter(location -> location.getCode() != null && location.getWarehouseId() != null)
                .collect(Collectors.toMap(location -> normalize(location.getCode()), Location::getWarehouseId, (a, b) -> a));
        int total = 0;
        for (InventoryRecord record : inventoryRepo.findAll()) {
            Long recordWarehouseId = null;
            if (record.getLocationId() != null) recordWarehouseId = locationWarehouse.get(record.getLocationId());
            if (recordWarehouseId == null) recordWarehouseId = locationCodeWarehouse.get(normalize(record.getLocationName()));
            if (Objects.equals(recordWarehouseId, warehouseId)) total += signedQty(record);
        }
        return Math.max(total, 0);
    }

    private int signedQty(InventoryRecord record) {
        int qty = record.getQty() == null ? 0 : record.getQty();
        if (isOutboundRecord(record.getType()) && qty > 0) return -qty;
        return qty;
    }

    private boolean isOutboundRecord(String type) {
        return "OUTBOUND".equals(type) || "REPACK_OUTBOUND".equals(type)
                || "REPACK_OUT".equals(type) || "OUTBOUND_DIRECT".equals(type);
    }

    private Map<String, Object> locationResult(Location saved, List<Long> warehouseIds) {
        return locationResult("保存成功", saved, warehouseIds);
    }

    private Map<String, Object> locationResult(String message, Object data, List<Long> warehouseIds) {
        List<String> warnings = overCapacityWarnings(warehouseIds);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", warnings.isEmpty() ? message : message + "；" + String.join("；", warnings));
        result.put("data", data);
        if (!warnings.isEmpty()) result.put("warnings", warnings);
        return result;
    }

    private List<String> overCapacityWarnings(List<Long> warehouseIds) {
        return warehouseIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(id -> warehouseRepo.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .filter(warehouse -> locationCapacity(warehouse.getId()) > (warehouse.getCapacity() == null ? 0 : warehouse.getCapacity()))
                .map(warehouse -> "仓库 " + warehouse.getName() + " 的库位容量合计 " + locationCapacity(warehouse.getId()) + " 已超过仓库总容量 " + warehouse.getCapacity())
                .toList();
    }

    private void ensureSupplierCodeUnique(Supplier supplier) {
        supplierRepo.findByCodeIgnoreCase(supplier.getCode()).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), supplier.getId())) throw new IllegalArgumentException("供应商编号已存在");
        });
    }

    private void ensurePartCodeUnique(Part part) {
        partRepo.findByCodeIgnoreCase(part.getCode()).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), part.getId())) throw new IllegalArgumentException("零件编号已存在");
        });
    }

    private void ensureWarehouseCodeUnique(Warehouse warehouse) {
        warehouseRepo.findByCodeIgnoreCase(warehouse.getCode()).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), warehouse.getId())) throw new IllegalArgumentException("仓库编号已存在");
        });
    }

    private void ensureCustomerCodeUnique(Customer customer) {
        customerRepo.findAll().stream()
                .filter(existing -> equalsIgnoreCase(existing.getCode(), customer.getCode()))
                .filter(existing -> !Objects.equals(existing.getId(), customer.getId()))
                .findFirst()
                .ifPresent(existing -> { throw new IllegalArgumentException("客户编号已存在"); });
    }

    private void ensureContainerCodeUnique(Container container) {
        containerRepo.findAll().stream()
                .filter(existing -> equalsIgnoreCase(existing.getCode(), container.getCode()))
                .filter(existing -> !Objects.equals(existing.getId(), container.getId()))
                .findFirst()
                .ifPresent(existing -> { throw new IllegalArgumentException("器具编号已存在"); });
    }

    private boolean contains(String value, String keyword) {
        return keyword == null || keyword.isBlank()
                || (value != null && value.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT)));
    }

    private boolean equalsIgnoreCase(String a, String b) {
        return a != null && b != null && a.equalsIgnoreCase(b);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? null : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException e) { return null; }
    }

    private int toInt(Object value) {
        if (value instanceof Number number) return number.intValue();
        try { return value == null ? 0 : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException e) { return 0; }
    }

    private String label(String code, String name) {
        if (!isBlank(code)) return code;
        if (!isBlank(name)) return name;
        return "未填写编号";
    }

    private Map<String, Object> batchResult(String label, int count, List<String> errors) {
        return Map.of("code", 200, "message", "成功添加 " + count + " 个" + label, "data", Map.of("count", count, "errors", errors));
    }

    private Map<String, Object> ok(Object data) {
        return Map.of("code", 200, "data", data);
    }

    private Map<String, Object> message(String message) {
        return Map.of("code", 200, "message", message);
    }

    private Map<String, Object> error(int code, String message) {
        return Map.of("code", code, "message", message);
    }
}
