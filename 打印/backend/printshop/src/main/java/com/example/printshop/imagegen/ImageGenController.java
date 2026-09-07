package com.example.printshop.imagegen;

import com.example.printshop.common.ApiException;
import com.example.printshop.common.ApiResponse;
import com.example.printshop.entity.ImageGenRecord;
import com.example.printshop.entity.ImageGenSettings;
import com.example.printshop.entity.ImageGenTemplate;
import com.example.printshop.entity.ImageGenUsage;
import com.example.printshop.entity.OrderInfo;
import com.example.printshop.mapper.ImageGenRecordMapper;
import com.example.printshop.mapper.ImageGenTemplateMapper;
import com.example.printshop.security.AuthContext;
import com.example.printshop.security.AuthPrincipal;
import com.example.printshop.service.OrderInfoService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/imagegen")
public class ImageGenController {

    private final ImageGenService imageGenService;
    private final ImageGenSettingsService settingsService;
    private final ImageGenStorageService storageService;
    private final PromptPolishService polishService;
    private final ImageGenRecordMapper recordMapper;
    private final ImageGenTemplateMapper templateMapper;
    private final ImageGenTemplateService templateService;
    private final OrderInfoService orderInfoService;

    public ImageGenController(ImageGenService imageGenService,
                              ImageGenSettingsService settingsService,
                              ImageGenStorageService storageService,
                              PromptPolishService polishService,
                              ImageGenRecordMapper recordMapper,
                              ImageGenTemplateMapper templateMapper,
                              ImageGenTemplateService templateService,
                              OrderInfoService orderInfoService) {
        this.imageGenService = imageGenService;
        this.settingsService = settingsService;
        this.storageService = storageService;
        this.polishService = polishService;
        this.recordMapper = recordMapper;
        this.templateMapper = templateMapper;
        this.templateService = templateService;
        this.orderInfoService = orderInfoService;
    }

    @PostMapping("/generate")
    public ApiResponse<Map<String, Object>> generate(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        AuthPrincipal principal = AuthContext.get();
        if (!principal.isUser() && !principal.isAdmin()) {
            throw ApiException.forbidden("user permission required");
        }
        Map<String, Object> result = imageGenService.generate(
                principal.getId(),
                authorization,
                str(body.get("model")),
                str(body.get("prompt")),
                str(body.get("size")),
                str(body.get("templateKey")),
                bool(body.get("polish")));
        return ApiResponse.ok(result);
    }

    @GetMapping("/models")
    public ApiResponse<Map<String, Object>> models() {
        AuthContext.get();
        Map<String, Object> catalog = imageGenService.modelCatalog();
        Map<String, Object> data = new LinkedHashMap<>(catalog == null ? Map.of() : catalog);
        data.putIfAbsent("models", List.of());
        data.put("polishAvailable", polishService.isConfigured());
        return ApiResponse.ok(data);
    }

    @GetMapping("/templates")
    public ApiResponse<List<?>> templates() {
        AuthContext.get();
        ImageGenSettings settings = settingsService.get();
        if (settings.getTemplatesEnabled() == null || settings.getTemplatesEnabled() != 1) {
            return ApiResponse.ok(List.of());
        }
        return ApiResponse.ok(templateMapper.selectActive());
    }

    @GetMapping("/templates/all")
    public ApiResponse<List<?>> allTemplates() {
        AuthContext.requirePermission("photo:imagegen:manage");
        return ApiResponse.ok(templateService.listAll());
    }

    @PostMapping("/templates")
    public ApiResponse<ImageGenTemplate> createTemplate(@RequestBody ImageGenTemplate template) {
        AuthContext.requirePermission("photo:imagegen:manage");
        return ApiResponse.ok(templateService.create(template));
    }

    @PutMapping("/templates/{id}")
    public ApiResponse<ImageGenTemplate> updateTemplate(@PathVariable Integer id,
                                                        @RequestBody ImageGenTemplate template) {
        AuthContext.requirePermission("photo:imagegen:manage");
        return ApiResponse.ok(templateService.update(id, template));
    }

    @DeleteMapping("/templates/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable Integer id) {
        AuthContext.requirePermission("photo:imagegen:manage");
        templateService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/polish")
    public ApiResponse<Map<String, Object>> polish(@RequestBody Map<String, Object> body) {
        AuthContext.get();
        String prompt = str(body.get("prompt"));
        if (prompt == null || prompt.isBlank()) {
            throw ApiException.badRequest("请输入要润色的描述");
        }
        return ApiResponse.ok(Map.of("prompt", polishService.polish(prompt)));
    }

    @GetMapping("/records")
    public ApiResponse<Map<String, Object>> records(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        AuthPrincipal principal = AuthContext.get();
        int[] range = pagingRange(page, size);
        int total = recordMapper.countByAccount(principal.getId());
        List<ImageGenRecord> items = total == 0
                ? List.of()
                : recordMapper.selectByAccount(principal.getId(), range[0], range[1]);
        return ApiResponse.ok(paged(total, page, range[1], items));
    }

    @GetMapping("/records/all")
    public ApiResponse<Map<String, Object>> allRecords(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int size,
                                                       @RequestParam(required = false) Integer accountId,
                                                       @RequestParam(required = false) String modelId,
                                                       @RequestParam(required = false) Integer moderationStatus,
                                                       @RequestParam(required = false) String start,
                                                       @RequestParam(required = false) String end) {
        AuthContext.requirePermission("photo:imagegen:query");
        int[] range = pagingRange(page, size);
        int total = recordMapper.countAll(accountId, modelId, moderationStatus, start, end);
        List<ImageGenRecord> items = total == 0
                ? List.of()
                : recordMapper.selectAll(accountId, modelId, moderationStatus, start, end, range[0], range[1]);
        return ApiResponse.ok(paged(total, page, range[1], items));
    }

    @DeleteMapping("/records/{id}")
    public ApiResponse<Void> deleteRecord(@PathVariable Long id) {
        AuthPrincipal principal = AuthContext.get();
        ImageGenRecord record = requireRecord(id);
        boolean owner = principal.getId() != null && principal.getId().equals(record.getAccountId());
        if (!owner) {
            if (!principal.isAdmin()) {
                throw ApiException.forbidden("cannot delete another user's record");
            }
            AuthContext.requirePermission("photo:imagegen:manage");
        }
        recordMapper.softDelete(id, owner ? principal.getId() : null);
        storageService.delete(record.getAccountId(), record.getToken());
        return ApiResponse.ok();
    }

    @PutMapping("/records/{id}/order")
    public ApiResponse<Void> bindOrder(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        AuthPrincipal principal = AuthContext.get();
        Integer orderId = intOf(body.get("orderId"));
        if (orderId == null) {
            throw ApiException.badRequest("orderId 不能为空");
        }
        ImageGenRecord record = requireRecord(id);
        if (principal.getId() == null || !principal.getId().equals(record.getAccountId())) {
            throw ApiException.forbidden("cannot modify another user's record");
        }
        OrderInfo order = orderInfoService.getOrderById(orderId);
        if (order == null) {
            throw ApiException.notFound("订单不存在");
        }
        if (order.getUserId() == null || !order.getUserId().equals(principal.getId())) {
            throw ApiException.forbidden("cannot access another user's order");
        }
        int rows = recordMapper.bindOrder(id, principal.getId(), orderId);
        if (rows <= 0) {
            throw ApiException.badRequest("该生成记录已关联订单");
        }
        return ApiResponse.ok();
    }

    @GetMapping("/download/{ownerId}/{token}")
    public ResponseEntity<Resource> download(@PathVariable Integer ownerId, @PathVariable String token) {
        AuthPrincipal principal = AuthContext.get();
        if (!principal.isAdmin() && !ownerId.equals(principal.getId())) {
            throw ApiException.forbidden("cannot access another user's file");
        }
        Path file = storageService.resolve(ownerId, token);
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("imagegen-" + token + ".png", StandardCharsets.UTF_8).build().toString())
                .contentLength(file.toFile().length())
                .body(resource);
    }

    @GetMapping("/config")
    public ApiResponse<ImageGenSettings> config() {
        AuthContext.requirePermission("photo:imagegen:manage");
        return ApiResponse.ok(settingsService.get());
    }

    @PutMapping("/config")
    public ApiResponse<ImageGenSettings> updateConfig(@RequestBody ImageGenSettings settings) {
        AuthPrincipal principal = AuthContext.requirePermission("photo:imagegen:manage");
        settingsService.update(settings, principal.getId() == null ? null : principal.getId().longValue());
        return ApiResponse.ok(settingsService.get());
    }

    @GetMapping("/usage")
    public ApiResponse<Map<String, Object>> usage(@RequestParam(required = false) String start,
                                                  @RequestParam(required = false) String end) {
        AuthContext.requirePermission("photo:imagegen:query");
        String effectiveEnd = end == null || end.isBlank() ? LocalDate.now().toString() : end;
        String effectiveStart = start == null || start.isBlank()
                ? LocalDate.now().minusDays(29).toString() : start;
        List<ImageGenUsage> items = recordMapper.aggregateUsage(effectiveStart, effectiveEnd);
        BigDecimal totalCost = BigDecimal.ZERO;
        long totalCount = 0;
        long totalTokens = 0;
        for (ImageGenUsage item : items) {
            if (item.getTotalCost() != null) {
                totalCost = totalCost.add(item.getTotalCost());
            }
            totalCount += item.getGenCount() == null ? 0 : item.getGenCount();
            totalTokens += item.getTotalTokens() == null ? 0 : item.getTotalTokens();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("start", effectiveStart);
        data.put("end", effectiveEnd);
        data.put("items", items);
        data.put("totalCost", totalCost);
        data.put("totalCount", totalCount);
        data.put("totalTokens", totalTokens);
        return ApiResponse.ok(data);
    }

    private ImageGenRecord requireRecord(Long id) {
        ImageGenRecord record = id == null ? null : recordMapper.selectById(id);
        if (record == null || record.getStatus() == null || record.getStatus() != 1) {
            throw ApiException.notFound("生成记录不存在");
        }
        return record;
    }

    private Map<String, Object> paged(int total, int page, int size, List<?> items) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        data.put("items", items);
        return data;
    }

    private int[] pagingRange(int page, int size) {
        int effectivePage = Math.max(page, 1);
        int effectiveSize = Math.min(Math.max(size, 1), 50);
        return new int[]{(effectivePage - 1) * effectiveSize, effectiveSize};
    }

    private static String str(Object value) {
        if (value instanceof String text) {
            return text.trim();
        }
        return null;
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value).toLowerCase(Locale.ROOT));
    }

    private static Integer intOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && text.matches("\\d+")) {
            return Integer.valueOf(text);
        }
        return null;
    }
}
