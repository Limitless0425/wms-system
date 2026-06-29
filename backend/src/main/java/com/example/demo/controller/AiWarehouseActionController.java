package com.example.demo.controller;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/ai-admin")
public class AiWarehouseActionController {
    private static final Pattern PART_CODE = Pattern.compile("(?i)\\bPT[0-9A-Za-z_-]+\\b");
    private static final Pattern ANY_KANBAN = Pattern.compile("(?i)\\b(?:RKB|OKB|CKB|KB)[0-9A-Za-z_-]+\\b");
    private static final Pattern ORDER_NO = Pattern.compile("(?i)\\b(?:RK|OUT|ZB|DIRECT)[0-9A-Za-z_-]+\\b");
    private static final Pattern NUMBER = Pattern.compile("(?<![A-Za-z0-9])([1-9][0-9]*)(?:\\s*(?:个|件|箱|数量|只|套))?");
    private static final Set<String> OUT_TYPES = Set.of("出库", "退库", "调账出库", "调账退库（无实物）");

    private final AiAdminController aiAdminController;
    private final InboundOrderController inboundController;
    private final OutboundController outboundController;
    private final RepackOrderController repackController;
    private final KanbanController kanbanController;
    private final BaseInfoController baseInfoController;
    private final SupplierRepository supplierRepo;
    private final CustomerRepository customerRepo;
    private final PartRepository partRepo;
    private final WarehouseRepository warehouseRepo;
    private final LocationRepository locationRepo;
    private final ContainerRepository containerRepo;
    private final KanbanRepository kanbanRepo;
    private final OutboundKanbanRepository outboundKanbanRepo;
    private final RepackKanbanRepository repackKanbanRepo;
    private final RepackOrderRepository repackOrderRepo;
    private final InventoryRecordRepository inventoryRecordRepo;
    private final AiUserConfigRepository aiUserConfigRepo;
    private final Environment environment;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, ActionPlan> planStore = new ConcurrentHashMap<>();
    private final Map<String, ActionPlan> pendingPlanStore = new ConcurrentHashMap<>();

    public AiWarehouseActionController(
            AiAdminController aiAdminController,
            InboundOrderController inboundController,
            OutboundController outboundController,
            RepackOrderController repackController,
            KanbanController kanbanController,
            BaseInfoController baseInfoController,
            SupplierRepository supplierRepo,
            CustomerRepository customerRepo,
            PartRepository partRepo,
            WarehouseRepository warehouseRepo,
            LocationRepository locationRepo,
            ContainerRepository containerRepo,
            KanbanRepository kanbanRepo,
            OutboundKanbanRepository outboundKanbanRepo,
            RepackKanbanRepository repackKanbanRepo,
            RepackOrderRepository repackOrderRepo,
            InventoryRecordRepository inventoryRecordRepo,
            AiUserConfigRepository aiUserConfigRepo,
            Environment environment) {
        this.aiAdminController = aiAdminController;
        this.inboundController = inboundController;
        this.outboundController = outboundController;
        this.repackController = repackController;
        this.kanbanController = kanbanController;
        this.baseInfoController = baseInfoController;
        this.supplierRepo = supplierRepo;
        this.customerRepo = customerRepo;
        this.partRepo = partRepo;
        this.warehouseRepo = warehouseRepo;
        this.locationRepo = locationRepo;
        this.containerRepo = containerRepo;
        this.kanbanRepo = kanbanRepo;
        this.outboundKanbanRepo = outboundKanbanRepo;
        this.repackKanbanRepo = repackKanbanRepo;
        this.repackOrderRepo = repackOrderRepo;
        this.inventoryRecordRepo = inventoryRecordRepo;
        this.aiUserConfigRepo = aiUserConfigRepo;
        this.environment = environment;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody(required = false) Map<String, Object> body) {
        String question = text(body == null ? null : body.get("question"));
        if (question.isBlank()) return ok(Map.of("mode", "answer", "answer", "请输入要执行或查询的仓库指令。"));

        ActionPlan plan = mergePendingPlan(question);
        if (plan == null) plan = buildPlan(question);
        if (plan == null || "QUERY".equals(plan.action())) {
            Map<String, Object> answer = aiAdminController.ask(Map.of("question", question));
            Object data = answer.get("data");
            if (data instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>((Map<String, Object>) map);
                result.put("mode", "answer");
                result.put("forecast", forecastWarnings().stream().limit(8).toList());
                return ok(result);
            }
            return answer;
        }

        if (plan.missing() != null && !plan.missing().isEmpty()) {
            pendingPlanStore.put(currentUsername(), plan);
            return ok(Map.of(
                    "mode", "need_more_info",
                    "answer", "我理解你想执行：" + plan.title() + "，但还缺少：" + String.join("、", plan.missing()),
                    "plan", plan.toClientMap()
            ));
        }

        pendingPlanStore.remove(currentUsername());
        planStore.put(plan.planId(), plan);
        return ok(Map.of(
                "mode", "plan",
                "answer", "我已生成执行计划，请确认后执行。",
                "plan", plan.toClientMap(),
                "forecast", forecastWarnings().stream().limit(8).toList()
        ));
    }

    @PostMapping("/execute")
    public Map<String, Object> execute(@RequestBody Map<String, Object> body) {
        String planId = text(body.get("planId"));
        if (planId.isBlank()) return fail("缺少执行计划编号");
        ActionPlan plan = planStore.remove(planId);
        if (plan == null) return fail("执行计划不存在或已过期，请重新输入指令");
        if (!isPlanActionConsistent(plan)) {
            return fail("执行计划动作和说明不一致，已拦截。请重新输入指令生成新的计划。");
        }

        Map<String, Object> result;
        try {
            result = executePlan(plan);
        } catch (Exception e) {
            return fail("执行失败：" + e.getMessage());
        }
        int code = asInt(result.get("code"), 200);
        if (code != 200) return result;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mode", "executed");
        data.put("answer", firstText(text(result.get("message")), "操作已执行完成"));
        data.put("plan", plan.toClientMap());
        data.put("result", summarizeExecutionResult(result.get("data")));
        data.put("forecast", forecastWarnings().stream().limit(8).toList());
        return ok(data);
    }

    private Object summarizeExecutionResult(Object value) {
        if (value == null) return null;
        if (value instanceof InboundOrder order) {
            return params(
                    "type", "入库单",
                    "id", order.getId(),
                    "orderNo", order.getOrderNo(),
                    "inboundType", firstText(order.getInboundType(), "正常入库"),
                    "supplierName", order.getSupplierName(),
                    "status", order.getStatus(),
                    "kanbanCount", order.getId() == null ? 0 : kanbanRepo.findByOrderId(order.getId()).size(),
                    "itemCount", order.getItems() == null ? 0 : order.getItems().size()
            );
        }
        if (value instanceof OutboundOrder order) {
            return params(
                    "type", "出库单",
                    "id", order.getId(),
                    "orderNo", order.getOrderNo(),
                    "supplierName", order.getSupplierName(),
                    "customerName", order.getCustomerName(),
                    "status", order.getStatus(),
                    "kanbanCount", order.getId() == null ? 0 : outboundKanbanRepo.findByOrderId(order.getId()).size(),
                    "itemCount", order.getItems() == null ? 0 : order.getItems().size()
            );
        }
        if (value instanceof RepackOrder order) {
            return params(
                    "type", "转包单",
                    "id", order.getId(),
                    "orderNo", order.getOrderNo(),
                    "supplierName", order.getSupplierName(),
                    "status", order.getStatus(),
                    "kanbanCount", order.getId() == null ? 0 : repackKanbanRepo.findByOrderId(order.getId()).size(),
                    "itemCount", order.getItems() == null ? 0 : order.getItems().size()
            );
        }
        if (value instanceof Kanban kanban) return kanbanSummary("入库看板", kanban.getKanbanNo(), kanban.getOrderNo(), kanban.getPartCode(), kanban.getPartName(), kanban.getQty(), kanban.getStatus());
        if (value instanceof OutboundKanban kanban) return kanbanSummary("出库看板", kanban.getKanbanNo(), kanban.getOrderNo(), kanban.getPartCode(), kanban.getPartName(), kanban.getActualQty(), kanban.getStatus());
        if (value instanceof RepackKanban kanban) return kanbanSummary("转包看板", kanban.getKanbanNo(), kanban.getOrderNo(), kanban.getPartCode(), kanban.getPartName(), kanban.getQty(), kanban.getStatus());
        if (value instanceof Collection<?> list) return list.stream().map(this::summarizeExecutionResult).toList();
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> safe = new LinkedHashMap<>();
            for (var entry : map.entrySet()) safe.put(String.valueOf(entry.getKey()), summarizeExecutionResult(entry.getValue()));
            return safe;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof LocalDateTime) return value;
        return text(value);
    }

    private Map<String, Object> kanbanSummary(String type, String kanbanNo, String orderNo, String partCode, String partName, Integer qty, String status) {
        return params(
                "type", type,
                "kanbanNo", kanbanNo,
                "orderNo", orderNo,
                "partCode", partCode,
                "partName", partName,
                "qty", qty,
                "status", status
        );
    }

    @GetMapping("/forecast")
    public Map<String, Object> forecast() {
        return ok(forecastWarnings());
    }

    @GetMapping("/model-status")
    public Map<String, Object> modelStatus() {
        AiModelConfig config = currentModelConfig();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", config.enabled());
        data.put("apiUrl", config.apiUrl());
        data.put("model", config.model());
        data.put("apiKeyConfigured", !config.apiKey().isBlank());
        data.put("apiKeyMask", maskKey(config.apiKey()));
        data.put("source", config.source());
        data.put("fallback", "未配置当前用户 API Key 时使用本地规则解析；可在前端设置里填写自己的 DeepSeek API Key。");
        return ok(data);
    }

    @GetMapping("/model-config")
    public Map<String, Object> modelConfig() {
        String username = currentUsername();
        var config = aiUserConfigRepo.findByUsername(username).orElse(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", username);
        data.put("apiUrl", config == null || text(config.getApiUrl()).isBlank() ? defaultApiUrl() : config.getApiUrl());
        data.put("model", config == null || text(config.getModel()).isBlank() ? defaultModel() : config.getModel());
        data.put("apiKeyConfigured", config != null && !text(config.getApiKey()).isBlank());
        data.put("apiKeyMask", config == null ? "" : maskKey(config.getApiKey()));
        data.put("updateTime", config == null ? null : config.getUpdateTime());
        return ok(data);
    }

    @PostMapping("/model-config")
    public Map<String, Object> saveModelConfig(@RequestBody Map<String, Object> body) {
        String username = currentUsername();
        var config = aiUserConfigRepo.findByUsername(username).orElseGet(AiUserConfig::new);
        config.setUsername(username);
        String apiUrl = firstText(text(body.get("apiUrl")), defaultApiUrl());
        String model = firstText(text(body.get("model")), defaultModel());
        String apiKey = text(body.get("apiKey"));
        config.setApiUrl(apiUrl);
        config.setModel(model);
        if (!apiKey.isBlank() && !"__KEEP__".equals(apiKey)) config.setApiKey(apiKey);
        if (Boolean.TRUE.equals(body.get("clearApiKey"))) config.setApiKey("");
        config.setUpdateTime(LocalDateTime.now());
        aiUserConfigRepo.save(config);
        return modelConfig();
    }

    private ActionPlan buildPlan(String question) {
        ActionPlan lockedPlan = buildLockedPlan(question);
        if (lockedPlan != null) return lockedPlan;
        ActionPlan modelPlan = buildPlanByModel(question);
        if (modelPlan != null) return normalizeModelPlan(modelPlan, question);
        return buildPlanByRules(question);
    }

    private ActionPlan mergePendingPlan(String question) {
        String username = currentUsername();
        ActionPlan pending = pendingPlanStore.get(username);
        if (pending == null || pending.missing() == null || pending.missing().isEmpty()) return null;
        if (pending.expireAt() != null && LocalDateTime.now().isAfter(pending.expireAt())) {
            pendingPlanStore.remove(username);
            return null;
        }
        if (!pending.action().startsWith("CREATE_")) return null;
        String lockedAction = lockedActionFromQuestion(question);
        if (!lockedAction.isBlank()) {
            pendingPlanStore.remove(username);
            return null;
        }
        if (looksLikeExplicitNewIntent(question)) return null;

        Map<String, Object> merged = new LinkedHashMap<>(pending.params());
        merged.remove("_displayRows");
        merged.remove("_detailRows");

        boolean changed = false;
        if (missingFieldContains(pending, "客户")) {
            Customer customer = findCustomer(question);
            if (customer != null) {
                merged.put("customerId", customer.getId());
                changed = true;
            }
        }
        if (missingFieldContains(pending, "供应商")) {
            Supplier supplier = findSupplier(question);
            if (supplier != null) {
                merged.put("supplierId", supplier.getId());
                changed = true;
            }
        }
        if (missingFieldContains(pending, "零件")) {
            Part part = findPart(question);
            if (part != null) {
                merged.put("partId", part.getId());
                changed = true;
            }
        }
        if (missingFieldContains(pending, "数量")) {
            Integer qty = extractQty(question);
            if (qty != null && qty > 0) {
                merged.put("qty", qty);
                changed = true;
            }
        }
        if (!changed) return null;

        String synthetic = syntheticCreateOrderQuestion(pending.action(), merged);
        if (synthetic.isBlank()) return null;
        return createOrderPlan(synthetic, pending.action());
    }

    private boolean missingFieldContains(ActionPlan plan, String keyword) {
        return plan.missing().stream().anyMatch(item -> {
            String value = text(item);
            if (value.contains("库存不足") || value.contains("库存充足")) return false;
            return value.contains(keyword);
        });
    }

    private boolean looksLikeExplicitNewIntent(String question) {
        String q = text(question);
        if (!containsAny(q, "新增", "添加", "新建", "创建", "生成")) return false;
        return containsAny(q, "客户", "供应商", "零件", "仓库", "库位", "器具", "入库单", "出库单", "转包单", "入库订单", "出库订单");
    }

    private String syntheticCreateOrderQuestion(String action, Map<String, Object> params) {
        String title = switch (action) {
            case "CREATE_INBOUND_ORDER" -> "新建入库单";
            case "CREATE_OUTBOUND_ORDER" -> "新建出库单";
            case "CREATE_REPACK_ORDER" -> "新建转包单";
            default -> "";
        };
        if (title.isBlank()) return "";
        StringBuilder sb = new StringBuilder(title);
        supplierRepo.findById(asLong(params.get("supplierId")) == null ? -1L : asLong(params.get("supplierId")))
                .ifPresent(s -> sb.append(' ').append(s.getCode()).append(' ').append(s.getName()));
        customerRepo.findById(asLong(params.get("customerId")) == null ? -1L : asLong(params.get("customerId")))
                .ifPresent(c -> sb.append(' ').append(c.getCode()).append(' ').append(c.getName()));
        partRepo.findById(asLong(params.get("partId")) == null ? -1L : asLong(params.get("partId")))
                .ifPresent(p -> sb.append(' ').append(p.getCode()).append(' ').append(p.getName()));
        Integer qty = asIntObject(params.get("qty"));
        if (qty != null && qty > 0) sb.append(" 数量").append(qty);
        if ("CREATE_OUTBOUND_ORDER".equals(action)) sb.append(' ').append(firstText(text(params.get("outboundType")), "出库"));
        return sb.toString();
    }

    private ActionPlan buildLockedPlan(String question) {
        String q = text(question);
        String kanbanNo = extract(ANY_KANBAN, q);
        String orderNo = extract(ORDER_NO, q);
        if (isCreateOrderIntent(q, "入库单")) return createOrderPlan(q, "CREATE_INBOUND_ORDER");
        if (isCreateOrderIntent(q, "转包单")) return createOrderPlan(q, "CREATE_REPACK_ORDER");
        if (isCreateOrderIntent(q, "出库单")) return createOrderPlan(q, "CREATE_OUTBOUND_ORDER");
        if (containsAny(q, "扫码入库", "扫描入库", "执行入库") && kanbanNo != null) {
            return plan("INBOUND_SCAN", "扫码入库", "扫描入库看板 " + kanbanNo, "中", params("kanbanNo", kanbanNo, "operator", "AI仓库管理员"), List.of());
        }
        if (containsAny(q, "不带单出库", "直接出库") && kanbanNo != null) {
            Integer qty = extractQty(q);
            return plan("DIRECT_OUTBOUND", "不带单出库", "从库存看板 " + kanbanNo + " 直接出库" + (qty == null ? "全部数量" : qty + " 件"), "高", params("kanbanNo", kanbanNo, "qty", qty, "operator", "AI仓库管理员"), List.of());
        }
        if (containsAny(q, "扫码出库", "扫描出库", "带单出库", "执行出库") && kanbanNo != null) {
            return plan("OUTBOUND_SCAN", "带单扫码出库", "扫描出库看板 " + kanbanNo, "高", params("kanbanNo", kanbanNo, "operator", "AI仓库管理员"), List.of());
        }
        if (containsAny(q, "执行转包单", "完成转包单") && orderNo != null) {
            var order = repackOrderRepo.findByOrderNo(orderNo).orElse(null);
            List<String> missing = order == null ? List.of("有效转包单号") : List.of();
            return plan("REPACK_ORDER_EXECUTE", "执行转包单", "执行转包单 " + orderNo, "高", params("orderNo", orderNo, "orderId", order == null ? null : order.getId()), missing);
        }
        if (containsAny(q, "扫码转包", "扫描转包", "转包入库", "执行转包") && kanbanNo != null) {
            Integer qty = extractQty(q);
            return plan("REPACK_SCAN", "扫描转包看板", "扫描转包看板 " + kanbanNo + (qty == null ? "" : "，实际数量 " + qty), "高", params("kanbanNo", kanbanNo, "actualQty", qty, "operator", "AI仓库管理员"), qty == null ? List.of("实际转包数量") : List.of());
        }
        return null;
    }

    private ActionPlan buildPlanByRules(String question) {
        String q = question.trim();
        String kanbanNo = extract(ANY_KANBAN, q);
        String orderNo = extract(ORDER_NO, q);

        if (containsAny(q, "扫码入库", "执行入库", "扫入库") && kanbanNo != null) {
            return plan("INBOUND_SCAN", "扫码入库", "扫描入库看板 " + kanbanNo, "中", params("kanbanNo", kanbanNo, "operator", "AI仓库管理员"), List.of());
        }
        if (containsAny(q, "不带单出库", "直接出库") && kanbanNo != null) {
            Integer qty = extractQty(q);
            return plan("DIRECT_OUTBOUND", "不带单出库", "从库存看板 " + kanbanNo + " 直接出库" + (qty == null ? "全部数量" : qty + " 件"), "高", params("kanbanNo", kanbanNo, "qty", qty, "operator", "AI仓库管理员"), List.of());
        }
        if (containsAny(q, "扫码出库", "带单出库", "执行出库") && kanbanNo != null) {
            return plan("OUTBOUND_SCAN", "带单扫码出库", "扫描出库看板 " + kanbanNo, "高", params("kanbanNo", kanbanNo, "operator", "AI仓库管理员"), List.of());
        }
        if (containsAny(q, "执行转包单", "完成转包单") && orderNo != null) {
            var order = repackOrderRepo.findByOrderNo(orderNo).orElse(null);
            List<String> missing = order == null ? List.of("有效转包单号") : List.of();
            return plan("REPACK_ORDER_EXECUTE", "执行转包单", "执行转包单 " + orderNo, "高", params("orderNo", orderNo, "orderId", order == null ? null : order.getId()), missing);
        }
        if (containsAny(q, "扫描转包", "转包入库", "执行转包") && kanbanNo != null) {
            Integer qty = extractQty(q);
            return plan("REPACK_SCAN", "扫描转包看板", "扫描转包看板 " + kanbanNo + (qty == null ? "" : "，实际数量 " + qty), "高", params("kanbanNo", kanbanNo, "actualQty", qty, "operator", "AI仓库管理员"), qty == null ? List.of("实际转包数量") : List.of());
        }
        if (isCreateOrderIntent(q, "入库单")) return createOrderPlan(q, "CREATE_INBOUND_ORDER");
        if (isCreateOrderIntent(q, "转包单")) return createOrderPlan(q, "CREATE_REPACK_ORDER");
        if (isCreateOrderIntent(q, "出库单")) return createOrderPlan(q, "CREATE_OUTBOUND_ORDER");
        if (containsAny(q, "删除")) return deleteBasePlan(q);
        if (containsAny(q, "新增供应商", "添加供应商", "新增客户", "添加客户", "新增仓库", "添加仓库", "新增库位", "添加库位")) return addBasePlan(q);
        if (containsAny(q, "修改", "编辑", "改为")) return updateBasePlan(q);
        return null;
    }

    private ActionPlan createOrderPlan(String q, String action) {
        var supplier = findSupplier(q);
        var customer = findCustomer(q);
        var part = findPart(q);
        Integer qty = extractQty(q);
        var warehouse = recommendedWarehouse(q, supplier);
        var location = recommendedLocation(q, warehouse);
        var container = "CREATE_OUTBOUND_ORDER".equals(action) ? null : recommendedContainer(q, action, part, supplier);

        List<String> missing = new ArrayList<>();
        if (supplier == null) missing.add("供应商");
        if (part == null) missing.add("零件编号或零件名称");
        if (qty == null || qty <= 0) missing.add("数量");
        if ("CREATE_OUTBOUND_ORDER".equals(action) && customer == null) missing.add("客户");

        String title = switch (action) {
            case "CREATE_INBOUND_ORDER" -> "新建入库单";
            case "CREATE_OUTBOUND_ORDER" -> "新建出库单";
            default -> "新建转包单";
        };
        String summary = title + "：" + (supplier == null ? "未识别供应商" : supplier.getName())
                + "，" + (part == null ? "未识别零件" : part.getCode() + " " + part.getName())
                + "，数量 " + (qty == null ? "未识别" : qty);
        if ("CREATE_OUTBOUND_ORDER".equals(action)) summary += "，客户 " + (customer == null ? "未识别" : customer.getName());

        Map<String, Object> params = params(
                "supplierId", supplier == null ? null : supplier.getId(),
                "customerId", customer == null ? null : customer.getId(),
                "partId", part == null ? null : part.getId(),
                "qty", qty,
                "warehouseId", warehouse == null ? null : warehouse.getId(),
                "locationId", location == null ? null : location.getId(),
                "containerId", container == null ? null : container.getId()
        );
        if ("CREATE_INBOUND_ORDER".equals(action)) {
            params.put("inboundType", "正常入库");
        }
        if ("CREATE_OUTBOUND_ORDER".equals(action)) {
            params.put("outboundType", outboundType(q));
        }
        if (part != null && qty != null && qty > 0 && ("CREATE_OUTBOUND_ORDER".equals(action) || "CREATE_REPACK_ORDER".equals(action))) {
            int availableQty = "CREATE_OUTBOUND_ORDER".equals(action)
                    ? availableOutboundStockQty(part.getId())
                    : availableRepackStockQty(part.getId());
            boolean enough = availableQty >= qty;
            String stockTip = enough
                    ? "库存充足，当前可用 " + availableQty + "，计划数量 " + qty
                    : "库存不足，当前可用 " + availableQty + "，计划数量 " + qty;
            params.put("stockAvailableQty", availableQty);
            params.put("stockRequiredQty", qty);
            params.put("stockEnough", enough);
            params.put("stockTip", stockTip);
            if (!enough) missing.add(stockTip);
        }
        params.put("_displayRows", buildOrderDisplayRows(action, title, supplier, customer, part, qty, warehouse, location, container, params));
        params.put("_detailRows", buildOrderDetailRows(action, part, qty, warehouse, location, container));
        return plan(action, title, summary, "高", params, missing);
    }

    private List<Map<String, Object>> buildOrderDisplayRows(
            String action,
            String title,
            Supplier supplier,
            Customer customer,
            Part part,
            Integer qty,
            Warehouse warehouse,
            Location location,
            Container container,
            Map<String, Object> params) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(displayRow("单据类型", title));
        rows.add(displayRow("供应商", supplier == null ? "" : supplier.getCode() + " " + supplier.getName()));
        if ("CREATE_INBOUND_ORDER".equals(action)) {
            rows.add(displayRow("入库类型", firstText(text(params.get("inboundType")), "正常入库")));
        }
        if ("CREATE_OUTBOUND_ORDER".equals(action)) {
            rows.add(displayRow("客户", customer == null ? "" : customer.getCode() + " " + customer.getName()));
            rows.add(displayRow("出库类型", text(params.get("outboundType"))));
        }
        rows.add(displayRow("零件", part == null ? "" : part.getCode() + " " + part.getName()));
        rows.add(displayRow("数量", qty == null ? "" : qty));
        rows.add(displayRow("库存校验", text(params.get("stockTip"))));
        if (!"CREATE_OUTBOUND_ORDER".equals(action)) {
            rows.add(displayRow("仓库", warehouse == null ? "" : warehouse.getCode() + " " + warehouse.getName()));
            rows.add(displayRow("库位", location == null ? "" : location.getCode()));
            rows.add(displayRow("首选器具", container == null ? "" : container.getCode() + " " + firstText(container.getName(), "")));
            rows.add(displayRow("器具容量", container == null ? "" : safe(container.getCapacity())));
            rows.add(displayRow("器具使用情况", packageTip(action, part, container, qty)));
        }
        return rows.stream().filter(row -> !text(row.get("value")).isBlank()).toList();
    }

    private List<Map<String, Object>> buildOrderDetailRows(
            String action,
            Part part,
            Integer qty,
            Warehouse warehouse,
            Location location,
            Container container) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("partCode", part == null ? "" : part.getCode());
        row.put("partName", part == null ? "" : part.getName());
        row.put("spec", part == null ? "" : part.getSpec());
        row.put("unit", part == null ? "" : part.getUnit());
        row.put("qty", qty == null ? "" : qty);
        if (!"CREATE_OUTBOUND_ORDER".equals(action)) {
            row.put("warehouse", warehouse == null ? "" : warehouse.getName());
            row.put("location", location == null ? "" : location.getCode());
            row.put("container", container == null ? "" : container.getCode() + " " + firstText(container.getName(), ""));
            row.put("containerCapacity", packageCapacity(action, part, container));
            row.put("containerUsage", packageTip(action, part, container, qty));
        } else {
            row.put("warehouse", "按库存 FIFO 自动匹配");
            row.put("location", "按库存 FIFO 自动匹配");
            row.put("container", "出库单不需要选择器具");
            row.put("containerCapacity", "");
            row.put("containerUsage", "");
        }
        return List.of(row);
    }

    private Map<String, Object> displayRow(String label, Object value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("label", label);
        row.put("value", value);
        return row;
    }

    private String packageTip(String action, Part part, Container container, Integer qty) {
        int capacity = packageCapacity(action, part, container);
        if (capacity <= 0 || qty == null || qty <= 0) return "未匹配到可用器具容量";
        int count = (qty + capacity - 1) / capacity;
        int remainder = qty % capacity;
        if (remainder == 0) return "共 " + count + " 个器具，刚好装满";
        return "需 " + count + " 个器具，最后一个未装满（余 " + remainder + "/" + capacity + "）";
    }

    private int packageCapacity(String action, Part part, Container container) {
        if ("CREATE_REPACK_ORDER".equals(action) && part != null && safe(part.getTargetPackageQty()) > 0) {
            return part.getTargetPackageQty();
        }
        return container == null ? 0 : safe(container.getCapacity());
    }

    private ActionPlan deleteBasePlan(String q) {
        String entity = baseEntity(q);
        Object target = findBaseTarget(entity, q);
        List<String> missing = new ArrayList<>();
        if (entity.isBlank()) missing.add("要删除的数据类型");
        if (target == null) missing.add("要删除的具体记录");
        Map<String, Object> params = params("entity", entity, "id", entityId(target));
        String name = entityName(target);
        return plan("DELETE_BASE", "删除基础资料", "删除" + baseEntityText(entity) + "：" + (name.isBlank() ? "未识别" : name), "高", params, missing);
    }

    private ActionPlan addBasePlan(String q) {
        String entity = baseEntity(q);
        String code = extractAfter(q, "编号", "代码", "编码");
        String name = extractAfter(q, "名称", "名字");
        if (name.isBlank()) name = extractNameAfterEntity(q, entity);
        Integer capacity = extractAfterNumber(q, "容量");
        List<String> missing = new ArrayList<>();
        if (entity.isBlank()) missing.add("基础资料类型");
        if (name.isBlank() && !"location".equals(entity)) missing.add("名称");
        if (code.isBlank()) code = defaultCode(entity);
        Map<String, Object> params = params("entity", entity, "code", code, "name", name, "capacity", capacity);
        if ("location".equals(entity)) {
            var warehouse = findWarehouse(q);
            if (warehouse == null) missing.add("所属仓库");
            params.put("warehouseId", warehouse == null ? null : warehouse.getId());
        }
        return plan("ADD_BASE", "新增基础资料", "新增" + baseEntityText(entity) + "：" + code + " " + name, "中", params, missing);
    }

    private ActionPlan updateBasePlan(String q) {
        String entity = baseEntity(q);
        Object target = findBaseTarget(entity, q);
        Map<String, Object> fields = new LinkedHashMap<>();
        String phone = extractPhone(q);
        String name = extractAfter(q, "名称", "名字");
        String address = extractAfter(q, "地址");
        Integer low = extractAfterNumber(q, "低储");
        Integer high = extractAfterNumber(q, "高储");
        Integer capacity = extractAfterNumber(q, "容量");
        if (!phone.isBlank()) fields.put("phone", phone);
        if (!name.isBlank()) fields.put("name", name);
        if (!address.isBlank()) fields.put("address", address);
        if (low != null) fields.put("lowStock", low);
        if (high != null) fields.put("highStock", high);
        if (capacity != null) fields.put("capacity", capacity);
        List<String> missing = new ArrayList<>();
        if (entity.isBlank()) missing.add("要修改的数据类型");
        if (target == null) missing.add("要修改的具体记录");
        if (fields.isEmpty()) missing.add("要修改的字段和值");
        return plan("UPDATE_BASE", "编辑基础资料", "编辑" + baseEntityText(entity) + "：" + entityName(target), "中", params("entity", entity, "id", entityId(target), "fields", fields), missing);
    }

    private ActionPlan buildPlanByModel(String question) {
        AiModelConfig config = currentModelConfig();
        if (!config.enabled()) return null;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", config.model());
            payload.put("temperature", 0);
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", modelSystemPrompt()),
                    Map.of("role", "user", "content", question)
            ));
            String json = objectMapper.writeValueAsString(payload);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(config.apiUrl()))
                    .timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            if (!config.apiKey().isBlank()) builder.header("Authorization", "Bearer " + config.apiKey());
            HttpResponse<String> response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) return null;
            Map<String, Object> res = objectMapper.readValue(response.body(), new TypeReference<>() {});
            String content = extractModelContent(res);
            if (content.isBlank()) return null;
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start < 0 || end <= start) return null;
            Map<String, Object> plan = objectMapper.readValue(content.substring(start, end + 1), new TypeReference<>() {});
            String action = text(plan.get("action"));
            if (action.isBlank() || "QUERY".equals(action)) return null;
            Map<String, Object> params = plan.get("params") instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : new LinkedHashMap<>();
            return plan(action, firstText(text(plan.get("title")), action), firstText(text(plan.get("summary")), question), "高", params, List.of());
        } catch (Exception ignored) {
            return null;
        }
    }

    private ActionPlan normalizeModelPlan(ActionPlan raw, String question) {
        String lockedAction = lockedActionFromQuestion(question);
        if (!lockedAction.isBlank()) {
            if (lockedAction.startsWith("CREATE_")) return createOrderPlan(question, lockedAction);
            if (!lockedAction.equals(raw.action())) return buildPlanByRules(question);
        }
        return switch (raw.action()) {
            case "CREATE_INBOUND_ORDER", "CREATE_OUTBOUND_ORDER", "CREATE_REPACK_ORDER" -> createOrderPlan(question, raw.action());
            case "INBOUND_SCAN", "OUTBOUND_SCAN", "DIRECT_OUTBOUND", "REPACK_SCAN", "REPACK_ORDER_EXECUTE", "DELETE_BASE", "ADD_BASE", "UPDATE_BASE" -> raw;
            default -> null;
        };
    }

    private String lockedActionFromQuestion(String question) {
        String q = text(question);
        if (containsAny(q, "入库单", "入库订单", "入库")) {
            if (containsAny(q, "新建", "创建", "生成", "添加", "开")) return "CREATE_INBOUND_ORDER";
            if (containsAny(q, "扫码", "扫描", "执行")) return "INBOUND_SCAN";
        }
        if (containsAny(q, "转包单")) {
            if (containsAny(q, "新建", "创建", "生成", "添加", "开")) return "CREATE_REPACK_ORDER";
            if (containsAny(q, "执行", "完成")) return "REPACK_ORDER_EXECUTE";
        }
        if (containsAny(q, "扫码转包", "扫描转包", "转包入库")) return "REPACK_SCAN";
        if (containsAny(q, "出库单", "出库订单")) {
            if (containsAny(q, "新建", "创建", "生成", "添加", "开")) return "CREATE_OUTBOUND_ORDER";
            if (containsAny(q, "扫码", "扫描", "执行")) return "OUTBOUND_SCAN";
        }
        if (containsAny(q, "不带单出库", "直接出库")) return "DIRECT_OUTBOUND";
        if (containsAny(q, "扫码出库", "扫描出库", "执行出库", "带单出库")) return "OUTBOUND_SCAN";
        return "";
    }

    private Map<String, Object> executePlan(ActionPlan plan) {
        Map<String, Object> p = plan.params();
        return switch (plan.action()) {
            case "CREATE_INBOUND_ORDER" -> executeCreateInbound(p);
            case "CREATE_OUTBOUND_ORDER" -> executeCreateOutbound(p);
            case "CREATE_REPACK_ORDER" -> executeCreateRepack(p);
            case "INBOUND_SCAN" -> kanbanController.scanInbound(text(p.get("kanbanNo")), Map.of("scanner", firstText(text(p.get("operator")), "AI仓库管理员")));
            case "OUTBOUND_SCAN" -> outboundController.scanOutbound(params("kanbanNo", p.get("kanbanNo"), "operator", firstText(text(p.get("operator")), "AI仓库管理员")));
            case "DIRECT_OUTBOUND" -> outboundController.directOutbound(params("kanbanNo", p.get("kanbanNo"), "qty", p.get("qty"), "operator", firstText(text(p.get("operator")), "AI仓库管理员")));
            case "REPACK_ORDER_EXECUTE" -> repackController.executeOrder(asLong(p.get("orderId")));
            case "REPACK_SCAN" -> repackController.scanRepackKanban(params("kanbanNo", p.get("kanbanNo"), "actualQty", p.get("actualQty"), "operator", firstText(text(p.get("operator")), "AI仓库管理员")));
            case "DELETE_BASE" -> executeDeleteBase(p);
            case "ADD_BASE" -> executeAddBase(p);
            case "UPDATE_BASE" -> executeUpdateBase(p);
            default -> fail("暂不支持该操作：" + plan.action());
        };
    }

    private boolean isPlanActionConsistent(ActionPlan plan) {
        String text = text(plan.title()) + " " + text(plan.summary());
        if (text.contains("入库") && "CREATE_OUTBOUND_ORDER".equals(plan.action())) return false;
        if (text.contains("入库") && "OUTBOUND_SCAN".equals(plan.action())) return false;
        if (text.contains("出库") && "CREATE_INBOUND_ORDER".equals(plan.action())) return false;
        if (text.contains("出库") && "INBOUND_SCAN".equals(plan.action())) return false;
        if (text.contains("转包") && ("CREATE_INBOUND_ORDER".equals(plan.action()) || "CREATE_OUTBOUND_ORDER".equals(plan.action()))) return false;
        return true;
    }

    private Map<String, Object> executeCreateInbound(Map<String, Object> p) {
        InboundOrder order = new InboundOrder();
        order.setSupplierId(asLong(p.get("supplierId")));
        order.setInboundType(firstText(text(p.get("inboundType")), "正常入库"));
        order.setCreator("AI仓库管理员");
        InboundOrderItem item = new InboundOrderItem();
        item.setPartId(asLong(p.get("partId")));
        item.setPlanQty(asInt(p.get("qty"), 0));
        item.setWarehouseId(bestWarehouseId(p));
        item.setLocationId(bestLocationId(p, item.getWarehouseId()));
        item.setContainerId(asLong(p.get("containerId")));
        fillInboundItemNames(item);
        order.setItems(new ArrayList<>(List.of(item)));
        order.setWarehouseId(item.getWarehouseId());
        return inboundController.createOrder(order);
    }

    private Map<String, Object> executeCreateOutbound(Map<String, Object> p) {
        OutboundOrder order = new OutboundOrder();
        order.setSupplierId(asLong(p.get("supplierId")));
        order.setCustomerId(asLong(p.get("customerId")));
        order.setOutboundType(firstText(text(p.get("outboundType")), "出库"));
        OutboundOrderItem item = new OutboundOrderItem();
        item.setPartId(asLong(p.get("partId")));
        item.setPlanQty(asInt(p.get("qty"), 0));
        order.setItems(new ArrayList<>(List.of(item)));
        return outboundController.createOrder(order);
    }

    private Map<String, Object> executeCreateRepack(Map<String, Object> p) {
        RepackOrder order = new RepackOrder();
        order.setSupplierId(asLong(p.get("supplierId")));
        order.setOperator("AI仓库管理员");
        order.setAllowBalance(true);
        RepackOrderItem item = new RepackOrderItem();
        item.setPartId(asLong(p.get("partId")));
        item.setPlanQty(asInt(p.get("qty"), 0));
        item.setWarehouseId(bestWarehouseId(p));
        item.setLocationId(bestLocationId(p, item.getWarehouseId()));
        fillRepackItemDefaults(item, asLong(p.get("containerId")));
        order.setItems(new ArrayList<>(List.of(item)));
        return repackController.createOrder(order);
    }

    private void fillInboundItemNames(InboundOrderItem item) {
        if (item.getWarehouseId() != null) {
            warehouseRepo.findById(item.getWarehouseId()).ifPresent(warehouse -> item.setWarehouseName(warehouse.getName()));
        }
        if (item.getLocationId() != null) {
            locationRepo.findById(item.getLocationId()).ifPresent(location -> item.setLocationName(location.getCode()));
        }
        if (item.getContainerId() != null) {
            containerRepo.findById(item.getContainerId()).ifPresent(container -> {
                item.setContainerCode(container.getCode());
                item.setContainerName(container.getName());
            });
        }
    }

    private void fillRepackItemDefaults(RepackOrderItem item, Long containerId) {
        partRepo.findById(item.getPartId()).ifPresent(part -> {
            item.setOriginalPackageQty(part.getOriginalPackageQty());
            item.setTargetPackageQty(part.getTargetPackageQty());
            item.setTargetContainerType(part.getRepackContainerType());
        });
        if (item.getWarehouseId() != null) {
            warehouseRepo.findById(item.getWarehouseId()).ifPresent(warehouse -> item.setWarehouseName(warehouse.getName()));
        }
        if (item.getLocationId() != null) {
            locationRepo.findById(item.getLocationId()).ifPresent(location -> item.setLocationName(location.getCode()));
        }
        if (containerId != null) {
            containerRepo.findById(containerId).ifPresent(container -> {
                item.setContainerCode(container.getCode());
                item.setContainerName(container.getName());
                if (text(item.getTargetContainerType()).isBlank()) item.setTargetContainerType(container.getType());
            });
        }
    }

    private Map<String, Object> executeDeleteBase(Map<String, Object> p) {
        Long id = asLong(p.get("id"));
        return switch (text(p.get("entity"))) {
            case "supplier" -> baseInfoController.deleteSupplier(id);
            case "customer" -> baseInfoController.deleteCustomer(id);
            case "part" -> baseInfoController.deletePart(id);
            case "warehouse" -> baseInfoController.deleteWarehouse(id);
            case "location" -> baseInfoController.deleteLocation(id);
            case "container" -> baseInfoController.deleteContainer(id);
            default -> fail("无法识别要删除的基础资料类型");
        };
    }

    private Map<String, Object> executeAddBase(Map<String, Object> p) {
        String entity = text(p.get("entity"));
        String code = text(p.get("code"));
        String name = text(p.get("name"));
        Integer capacity = asIntObject(p.get("capacity"));
        switch (entity) {
            case "supplier" -> {
                Supplier s = new Supplier();
                s.setCode(code); s.setName(name);
                return baseInfoController.addSupplier(s);
            }
            case "customer" -> {
                Customer c = new Customer();
                c.setCode(code); c.setName(name);
                return baseInfoController.addCustomer(c);
            }
            case "warehouse" -> {
                Warehouse w = new Warehouse();
                w.setCode(code); w.setName(name); w.setCapacity(capacity == null ? 0 : capacity);
                return baseInfoController.addWarehouse(w);
            }
            case "location" -> {
                Location l = new Location();
                l.setCode(code); l.setName(name.isBlank() ? code : name); l.setCapacity(capacity == null ? 0 : capacity);
                l.setWarehouseId(asLong(p.get("warehouseId")));
                return baseInfoController.addLocation(l);
            }
            default -> {
                return fail("当前自然语言新增暂支持供应商、客户、仓库、库位");
            }
        }
    }

    private Map<String, Object> executeUpdateBase(Map<String, Object> p) {
        String entity = text(p.get("entity"));
        Long id = asLong(p.get("id"));
        Map<String, Object> fields = p.get("fields") instanceof Map<?, ?> m ? new LinkedHashMap<>((Map<String, Object>) m) : Map.of();
        return switch (entity) {
            case "supplier" -> supplierRepo.findById(id).map(s -> {
                applyCommonFields(s, fields);
                return baseInfoController.updateSupplier(s);
            }).orElse(fail("供应商不存在"));
            case "customer" -> customerRepo.findById(id).map(c -> {
                applyCommonFields(c, fields);
                return baseInfoController.updateCustomer(c);
            }).orElse(fail("客户不存在"));
            case "part" -> partRepo.findById(id).map(part -> {
                if (fields.get("name") != null) part.setName(text(fields.get("name")));
                if (fields.get("lowStock") != null) part.setLowStock(asInt(fields.get("lowStock"), part.getLowStock()));
                if (fields.get("highStock") != null) part.setHighStock(asInt(fields.get("highStock"), part.getHighStock()));
                return baseInfoController.updatePart(part);
            }).orElse(fail("零件不存在"));
            case "warehouse" -> warehouseRepo.findById(id).map(w -> {
                if (fields.get("name") != null) w.setName(text(fields.get("name")));
                if (fields.get("capacity") != null) w.setCapacity(asInt(fields.get("capacity"), w.getCapacity()));
                return baseInfoController.updateWarehouse(w);
            }).orElse(fail("仓库不存在"));
            default -> fail("当前自然语言编辑暂支持供应商、客户、零件、仓库");
        };
    }

    private List<Map<String, Object>> forecastWarnings() {
        Map<String, Integer> stock = currentStock();
        Map<String, Integer> out30 = outboundQtySince(LocalDateTime.now().minusDays(30));
        Map<String, LocalDateTime> lastOut = lastOutboundTime();
        List<Map<String, Object>> warnings = new ArrayList<>();
        for (Part part : partRepo.findAll()) {
            int qty = stock.getOrDefault(part.getCode(), 0);
            int consumed = out30.getOrDefault(part.getCode(), 0);
            double daily = consumed / 30.0;
            Double daysToShortage = daily > 0 ? qty / daily : null;
            String risk = null;
            String message = null;
            if (qty <= 0) {
                risk = "缺货";
                message = "当前已无可用库存";
            } else if (daysToShortage != null && daysToShortage <= 14) {
                risk = "未来缺货";
                message = "按近30天日均消耗 " + round(daily) + " 估算，约 " + Math.max(1, daysToShortage.intValue()) + " 天后可能缺货";
            } else {
                LocalDateTime last = lastOut.get(part.getCode());
                boolean noRecentOut = last == null || last.isBefore(LocalDateTime.now().minusDays(60));
                int high = part.getHighStock() == null ? 0 : part.getHighStock();
                if (qty > 0 && noRecentOut) {
                    risk = "呆滞风险";
                    message = "当前有库存 " + qty + "，近60天没有出库消耗记录";
                } else if (high > 0 && qty > high && daily < 1) {
                    risk = "积压风险";
                    message = "库存高于高储且近30天日均消耗小于1";
                }
            }
            if (risk != null) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("risk", risk);
                row.put("partCode", part.getCode());
                row.put("partName", part.getName());
                row.put("supplierName", part.getSupplierName());
                row.put("stockQty", qty);
                row.put("outbound30Days", consumed);
                row.put("dailyUsage", round(daily));
                row.put("daysToShortage", daysToShortage == null ? null : Math.max(0, daysToShortage.intValue()));
                row.put("message", message);
                warnings.add(row);
            }
        }
        warnings.sort(Comparator.comparingInt(row -> riskRank(text(row.get("risk")))));
        return warnings;
    }

    private Map<String, Integer> currentStock() {
        Map<String, Integer> stock = new LinkedHashMap<>();
        kanbanRepo.findAll().stream()
                .filter(k -> "SCANNED".equals(k.getStatus()) && !Boolean.TRUE.equals(k.getSealed()))
                .forEach(k -> stock.merge(k.getPartCode(), safe(k.getQty()), Integer::sum));
        repackKanbanRepo.findAll().stream()
                .filter(k -> "REPACK_INBOUND".equals(k.getStatus()))
                .forEach(k -> stock.merge(k.getPartCode(), safe(k.getQty()), Integer::sum));
        return stock;
    }

    private int availableOutboundStockQty(Long partId) {
        if (partId == null) return 0;
        return kanbanRepo.findByPartIdAndStatus(partId, "SCANNED").stream()
                .filter(k -> !Boolean.TRUE.equals(k.getSealed()))
                .mapToInt(k -> safe(k.getQty()))
                .sum();
    }

    private int availableRepackStockQty(Long partId) {
        if (partId == null) return 0;
        int inboundQty = availableOutboundStockQty(partId);
        int repackQty = repackKanbanRepo.findAll().stream()
                .filter(k -> Objects.equals(k.getPartId(), partId))
                .filter(k -> "REPACK_INBOUND".equals(k.getStatus()))
                .mapToInt(k -> safe(k.getQty()))
                .sum();
        return inboundQty + repackQty;
    }

    private Map<String, Integer> outboundQtySince(LocalDateTime start) {
        Map<String, Integer> result = new LinkedHashMap<>();
        inventoryRecordRepo.findAll().stream()
                .filter(r -> r.getCreateTime() != null && !r.getCreateTime().isBefore(start))
                .filter(r -> text(r.getType()).contains("OUT"))
                .forEach(r -> result.merge(r.getPartCode(), Math.abs(safe(r.getQty())), Integer::sum));
        return result;
    }

    private Map<String, LocalDateTime> lastOutboundTime() {
        Map<String, LocalDateTime> result = new LinkedHashMap<>();
        inventoryRecordRepo.findAll().stream()
                .filter(r -> text(r.getType()).contains("OUT"))
                .filter(r -> r.getCreateTime() != null)
                .forEach(r -> result.merge(r.getPartCode(), r.getCreateTime(), (a, b) -> a.isAfter(b) ? a : b));
        return result;
    }

    private Supplier findSupplier(String q) {
        return supplierRepo.findAll().stream().filter(s -> containsEntity(q, s.getCode(), s.getName())).findFirst().orElse(null);
    }

    private Customer findCustomer(String q) {
        return customerRepo.findAll().stream().filter(c -> containsEntity(q, c.getCode(), c.getName())).findFirst().orElse(null);
    }

    private Part findPart(String q) {
        String code = extract(PART_CODE, q);
        if (code != null) return partRepo.findByCodeIgnoreCase(code).orElse(null);
        return partRepo.findAll().stream().filter(p -> containsEntity(q, p.getCode(), p.getName())).findFirst().orElse(null);
    }

    private Warehouse findWarehouse(String q) {
        return warehouseRepo.findAll().stream().filter(w -> containsEntity(q, w.getCode(), w.getName())).findFirst().orElse(null);
    }

    private Warehouse recommendedWarehouse(String q, Supplier supplier) {
        Warehouse explicit = findWarehouse(q);
        if (explicit != null) return explicit;
        if (supplier != null && supplier.getPreferredWarehouseId() != null) {
            Warehouse preferred = warehouseRepo.findById(supplier.getPreferredWarehouseId()).orElse(null);
            if (preferred != null) return preferred;
        }
        return warehouseRepo.findAll().stream().findFirst().orElse(null);
    }

    private Location findLocation(String q, Long warehouseId) {
        return locationRepo.findAll().stream()
                .filter(l -> warehouseId == null || Objects.equals(l.getWarehouseId(), warehouseId))
                .filter(l -> containsEntity(q, l.getCode(), l.getName()))
                .findFirst().orElse(null);
    }

    private Location recommendedLocation(String q, Warehouse warehouse) {
        Long warehouseId = warehouse == null ? null : warehouse.getId();
        Location explicit = findLocation(q, warehouseId);
        if (explicit != null) return explicit;
        if (warehouseId == null) return null;
        return locationRepo.findByWarehouseId(warehouseId).stream().findFirst().orElse(null);
    }

    private Container findContainer(String q, String partCode, String supplierCode) {
        return containerRepo.findAll().stream()
                .filter(c -> containsEntity(q, c.getCode(), c.getName())
                        || (!text(partCode).isBlank() && partCode.equalsIgnoreCase(text(c.getPartCode())))
                        || (!text(supplierCode).isBlank() && supplierCode.equalsIgnoreCase(text(c.getSupplierCode()))))
                .findFirst().orElse(null);
    }

    private Container recommendedContainer(String q, String action, Part part, Supplier supplier) {
        List<Container> all = containerRepo.findAll();
        Container explicit = all.stream().filter(c -> containsEntity(q, c.getCode(), c.getName())).findFirst().orElse(null);
        if (explicit != null) return explicit;

        String partCode = part == null ? "" : text(part.getCode());
        String supplierCode = supplier == null ? "" : text(supplier.getCode());
        List<Container> candidates = all.stream()
                .filter(c -> text(c.getSupplierCode()).isBlank() || supplierCode.isBlank() || text(c.getSupplierCode()).equalsIgnoreCase(supplierCode))
                .filter(c -> text(c.getPartCode()).isBlank() || partCode.isBlank() || text(c.getPartCode()).equalsIgnoreCase(partCode))
                .toList();
        if (candidates.isEmpty()) return null;

        int targetCapacity = "CREATE_REPACK_ORDER".equals(action) && part != null
                ? safe(part.getTargetPackageQty())
                : safe(part == null ? null : part.getOriginalPackageQty());
        String targetType = "CREATE_REPACK_ORDER".equals(action) && part != null ? text(part.getRepackContainerType()) : "";
        List<Container> typed = targetType.isBlank()
                ? candidates
                : candidates.stream().filter(c -> targetType.equalsIgnoreCase(text(c.getType()))).toList();
        if (typed.isEmpty()) typed = candidates;
        if (targetCapacity > 0) {
            Container exact = typed.stream().filter(c -> Objects.equals(c.getCapacity(), targetCapacity)).findFirst().orElse(null);
            if (exact != null) return exact;
            exact = candidates.stream().filter(c -> Objects.equals(c.getCapacity(), targetCapacity)).findFirst().orElse(null);
            if (exact != null) return exact;
        }
        return typed.stream().findFirst().orElse(candidates.get(0));
    }

    private Object findBaseTarget(String entity, String q) {
        return switch (entity) {
            case "supplier" -> findSupplier(q);
            case "customer" -> findCustomer(q);
            case "part" -> findPart(q);
            case "warehouse" -> findWarehouse(q);
            case "location" -> findLocation(q, null);
            case "container" -> findContainer(q, null, null);
            default -> null;
        };
    }

    private String baseEntity(String q) {
        if (q.contains("供应商")) return "supplier";
        if (q.contains("客户")) return "customer";
        if (q.contains("零件") || q.contains("物料")) return "part";
        if (q.contains("仓库")) return "warehouse";
        if (q.contains("库位")) return "location";
        if (q.contains("器具")) return "container";
        return "";
    }

    private Long bestWarehouseId(Map<String, Object> p) {
        Long id = asLong(p.get("warehouseId"));
        if (id != null) return id;
        Long supplierId = asLong(p.get("supplierId"));
        if (supplierId != null) {
            var supplier = supplierRepo.findById(supplierId).orElse(null);
            if (supplier != null && supplier.getPreferredWarehouseId() != null) return supplier.getPreferredWarehouseId();
        }
        return warehouseRepo.findAll().stream().findFirst().map(Warehouse::getId).orElse(null);
    }

    private Long bestLocationId(Map<String, Object> p, Long warehouseId) {
        Long id = asLong(p.get("locationId"));
        if (id != null) return id;
        if (warehouseId == null) return null;
        return locationRepo.findByWarehouseId(warehouseId).stream().findFirst().map(Location::getId).orElse(null);
    }

    private String modelSystemPrompt() {
        return """
                你是WMS仓储系统的意图解析器，只能输出JSON，不要解释。
                action只能是：QUERY, CREATE_INBOUND_ORDER, CREATE_OUTBOUND_ORDER, CREATE_REPACK_ORDER, INBOUND_SCAN, OUTBOUND_SCAN, DIRECT_OUTBOUND, REPACK_SCAN, REPACK_ORDER_EXECUTE, ADD_BASE, UPDATE_BASE, DELETE_BASE。
                params只放用户明确提到的实体名称、编号、看板号、单据号、数量、类型。
                示例：{"action":"OUTBOUND_SCAN","title":"带单出库","summary":"扫描出库看板","params":{"kanbanNo":"OKB123"}}
                """;
    }

    private String extractModelContent(Map<String, Object> res) {
        Object choices = res.get("choices");
        if (choices instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            Object message = first.get("message");
            if (message instanceof Map<?, ?> msg) return text(msg.get("content"));
            return text(first.get("text"));
        }
        return text(res.get("content"));
    }

    private ActionPlan plan(String action, String title, String summary, String risk, Map<String, Object> params, List<String> missing) {
        return new ActionPlan(UUID.randomUUID().toString(), action, title, summary, risk, params, missing, LocalDateTime.now().plusMinutes(10));
    }

    private Map<String, Object> params(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) map.put(String.valueOf(kv[i]), kv[i + 1]);
        return map;
    }

    private String extract(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group().toUpperCase(Locale.ROOT) : null;
    }

    private Integer extractQty(String q) {
        Matcher matcher = NUMBER.matcher(q);
        while (matcher.find()) {
            String value = matcher.group(1);
            int idx = matcher.start(1);
            String before = q.substring(Math.max(0, idx - 3), idx);
            if (before.matches(".*[A-Za-z].*")) continue;
            return Integer.parseInt(value);
        }
        return null;
    }

    private Integer extractAfterNumber(String q, String key) {
        Matcher matcher = Pattern.compile(Pattern.quote(key) + "\\s*(?:为|是|:|：)?\\s*([0-9]+)").matcher(q);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private String extractAfter(String q, String... keys) {
        for (String key : keys) {
            Matcher matcher = Pattern.compile(Pattern.quote(key) + "\\s*(?:为|是|:|：)?\\s*([\\u4e00-\\u9fa5A-Za-z0-9_-]+)").matcher(q);
            if (matcher.find()) return matcher.group(1);
        }
        return "";
    }

    private String extractNameAfterEntity(String q, String entity) {
        String label = baseEntityText(entity);
        if (label.isBlank()) return "";
        Matcher matcher = Pattern.compile("(?:新增|添加)" + Pattern.quote(label) + "\\s*([\\u4e00-\\u9fa5A-Za-z0-9_-]+)").matcher(q);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractPhone(String q) {
        Matcher matcher = Pattern.compile("1[3-9][0-9]{9}|[0-9]{3,4}-?[0-9]{7,8}").matcher(q);
        return matcher.find() ? matcher.group() : "";
    }

    private String outboundType(String q) {
        return OUT_TYPES.stream().filter(q::contains).findFirst().orElse("出库");
    }

    private boolean containsAny(String value, String... keys) {
        for (String key : keys) if (value.contains(key)) return true;
        return false;
    }

    private boolean isCreateOrderIntent(String q, String orderType) {
        return q.contains(orderType) && containsAny(q, "新建", "创建", "生成", "添加");
    }

    private boolean containsEntity(String q, String code, String name) {
        return (!text(code).isBlank() && q.toLowerCase(Locale.ROOT).contains(text(code).toLowerCase(Locale.ROOT)))
                || (!text(name).isBlank() && q.contains(text(name)));
    }

    private Long entityId(Object target) {
        if (target instanceof Supplier s) return s.getId();
        if (target instanceof Customer c) return c.getId();
        if (target instanceof Part p) return p.getId();
        if (target instanceof Warehouse w) return w.getId();
        if (target instanceof Location l) return l.getId();
        if (target instanceof Container c) return c.getId();
        return null;
    }

    private String entityName(Object target) {
        if (target instanceof Supplier s) return s.getName();
        if (target instanceof Customer c) return c.getName();
        if (target instanceof Part p) return p.getCode() + " " + p.getName();
        if (target instanceof Warehouse w) return w.getName();
        if (target instanceof Location l) return l.getCode();
        if (target instanceof Container c) return c.getCode();
        return "";
    }

    private String baseEntityText(String entity) {
        return switch (entity) {
            case "supplier" -> "供应商";
            case "customer" -> "客户";
            case "part" -> "零件";
            case "warehouse" -> "仓库";
            case "location" -> "库位";
            case "container" -> "器具";
            default -> "";
        };
    }

    private String defaultCode(String entity) {
        String prefix = switch (entity) {
            case "supplier" -> "SUP";
            case "customer" -> "CUS";
            case "warehouse" -> "WH";
            case "location" -> "LOC";
            default -> "AI";
        };
        return prefix + System.currentTimeMillis();
    }

    private void applyCommonFields(Supplier s, Map<String, Object> fields) {
        if (fields.get("name") != null) s.setName(text(fields.get("name")));
        if (fields.get("phone") != null) s.setPhone(text(fields.get("phone")));
        if (fields.get("address") != null) s.setAddress(text(fields.get("address")));
    }

    private void applyCommonFields(Customer c, Map<String, Object> fields) {
        if (fields.get("name") != null) c.setName(text(fields.get("name")));
        if (fields.get("phone") != null) c.setPhone(text(fields.get("phone")));
        if (fields.get("address") != null) c.setAddress(text(fields.get("address")));
    }

    private int riskRank(String risk) {
        return switch (risk) {
            case "缺货" -> 0;
            case "未来缺货" -> 1;
            case "呆滞风险" -> 2;
            default -> 3;
        };
    }

    private AiModelConfig currentModelConfig() {
        String username = currentUsername();
        var config = aiUserConfigRepo.findByUsername(username).orElse(null);
        if (config != null && !text(config.getApiKey()).isBlank()) {
            return new AiModelConfig(
                    firstText(config.getApiUrl(), defaultApiUrl()),
                    config.getApiKey(),
                    firstText(config.getModel(), defaultModel()),
                    "user:" + username
            );
        }
        String envKey = firstText(System.getenv("WMS_AI_API_KEY"), System.getProperty("wms.ai.api-key"));
        if (!envKey.isBlank()) {
            return new AiModelConfig(
                    firstText(System.getenv("WMS_AI_API_URL"), firstText(environment.getProperty("wms.ai.api-url"), System.getProperty("wms.ai.api-url"))),
                    envKey,
                    firstText(System.getenv("WMS_AI_MODEL"), firstText(environment.getProperty("wms.ai.model"), firstText(System.getProperty("wms.ai.model"), defaultModel()))),
                    "environment"
            );
        }
        return new AiModelConfig(defaultApiUrl(), "", defaultModel(), "local-rule");
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) return "anonymous";
        return auth.getName();
    }

    private String defaultApiUrl() { return firstText(environment.getProperty("wms.ai.api-url"), "https://api.deepseek.com/chat/completions"); }
    private String defaultModel() { return firstText(environment.getProperty("wms.ai.model"), "deepseek-v4-flash"); }
    private String maskUrl(String url) { return url == null || url.isBlank() ? "" : url.replaceAll("(https?://[^/]+).*", "$1/***"); }
    private String maskKey(String key) {
        String value = text(key);
        if (value.isBlank()) return "";
        return value.length() <= 8 ? "****" : value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }
    private String round(double value) { return String.format(Locale.ROOT, "%.2f", value); }
    private int safe(Integer value) { return value == null ? 0 : value; }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String firstText(String a, String b) { return a != null && !a.isBlank() ? a : (b == null ? "" : b); }
    private Long asLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        try { return value == null || String.valueOf(value).isBlank() ? null : Long.parseLong(String.valueOf(value)); }
        catch (Exception e) { return null; }
    }
    private int asInt(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try { return value == null || String.valueOf(value).isBlank() ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (Exception e) { return fallback; }
    }
    private Integer asIntObject(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        return asInt(value, 0);
    }
    private Map<String, Object> ok(Object data) { return Map.of("code", 200, "data", data); }
    private Map<String, Object> fail(String message) { return Map.of("code", 400, "message", message); }

    private record ActionPlan(String planId, String action, String title, String summary, String risk,
                              Map<String, Object> params, List<String> missing, LocalDateTime expireAt) {
        Map<String, Object> toClientMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("planId", planId);
            map.put("action", action);
            map.put("title", title);
            map.put("summary", summary);
            map.put("risk", risk);
            map.put("params", params);
            map.put("missing", missing);
            map.put("expireAt", expireAt);
            map.put("requiresConfirm", true);
            return map;
        }
    }

    private record AiModelConfig(String apiUrl, String apiKey, String model, String source) {
        boolean enabled() { return apiUrl != null && !apiUrl.isBlank() && apiKey != null && !apiKey.isBlank(); }
    }
}
