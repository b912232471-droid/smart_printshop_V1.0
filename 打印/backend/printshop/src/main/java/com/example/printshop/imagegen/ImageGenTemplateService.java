package com.example.printshop.imagegen;

import com.example.printshop.common.ApiException;
import com.example.printshop.entity.ImageGenTemplate;
import com.example.printshop.mapper.ImageGenTemplateMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 校园模板管理（image_gen_template CRUD）：客户端模板填空的数据来源，管理端可调配
 */
@Service
public class ImageGenTemplateService {
    private static final Logger log = LoggerFactory.getLogger(ImageGenTemplateService.class);
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{2,63}$");
    private static final Pattern SIZE_PATTERN = Pattern.compile("^\\d{2,5}x\\d{2,5}$");
    private static final Set<String> CATEGORIES = Set.of("handout", "poster", "notice", "photo");

    private final ImageGenTemplateMapper templateMapper;
    private final ObjectMapper objectMapper;

    public ImageGenTemplateService(ImageGenTemplateMapper templateMapper, ObjectMapper objectMapper) {
        this.templateMapper = templateMapper;
        this.objectMapper = objectMapper;
    }

    public List<ImageGenTemplate> listAll() {
        return templateMapper.selectAll();
    }

    public ImageGenTemplate create(ImageGenTemplate template) {
        validate(template, null);
        if (templateMapper.countByKey(template.getTemplateKey()) > 0) {
            throw ApiException.badRequest("模板标识已存在：" + template.getTemplateKey());
        }
        if (template.getStatus() == null) {
            template.setStatus(1);
        }
        int rows = templateMapper.insert(template);
        if (rows <= 0 || template.getId() == null) {
            throw ApiException.serviceUnavailable("模板创建失败，请稍后重试");
        }
        return templateMapper.selectById(template.getId());
    }

    public ImageGenTemplate update(Integer id, ImageGenTemplate incoming) {
        ImageGenTemplate existing = requireTemplate(id);
        if (incoming.getTemplateKey() != null
                && !incoming.getTemplateKey().isBlank()
                && !incoming.getTemplateKey().trim().equals(existing.getTemplateKey())) {
            throw ApiException.badRequest("模板标识不允许修改");
        }
        incoming.setTemplateKey(existing.getTemplateKey());
        validate(incoming, existing);
        incoming.setId(existing.getId());
        int rows = templateMapper.update(incoming);
        if (rows <= 0) {
            throw ApiException.serviceUnavailable("模板保存失败，请稍后重试");
        }
        return templateMapper.selectById(existing.getId());
    }

    public void delete(Integer id) {
        requireTemplate(id);
        int rows = templateMapper.deleteById(id);
        if (rows <= 0) {
            throw ApiException.serviceUnavailable("模板删除失败，请稍后重试");
        }
    }

    private ImageGenTemplate requireTemplate(Integer id) {
        ImageGenTemplate template = id == null ? null : templateMapper.selectById(id);
        if (template == null) {
            throw ApiException.notFound("模板不存在");
        }
        return template;
    }

    private void validate(ImageGenTemplate template, ImageGenTemplate existing) {
        if (template == null) {
            throw ApiException.badRequest("模板内容不能为空");
        }
        if (existing == null) {
            String key = template.getTemplateKey() == null ? "" : template.getTemplateKey().trim();
            if (!KEY_PATTERN.matcher(key).matches()) {
                throw ApiException.badRequest("模板标识须为 3-64 位小写字母/数字/下划线，且以字母开头");
            }
            template.setTemplateKey(key);
        }
        String title = template.getTitle() == null ? "" : template.getTitle().trim();
        if (title.isEmpty() || title.length() > 100) {
            throw ApiException.badRequest("模板标题须为 1-100 字");
        }
        template.setTitle(title);
        String category = template.getCategory() == null ? "" : template.getCategory().trim().toLowerCase(Locale.ROOT);
        if (!CATEGORIES.contains(category)) {
            throw ApiException.badRequest("分类仅支持 handout/poster/notice/photo");
        }
        template.setCategory(category);
        String prompt = template.getPromptTemplate() == null ? "" : template.getPromptTemplate().trim();
        if (prompt.isEmpty() || prompt.length() > 600) {
            throw ApiException.badRequest("提示词模板须为 1-600 字");
        }
        template.setPromptTemplate(prompt);
        String model = template.getRecommendedModel() == null ? "" : template.getRecommendedModel().trim();
        if (model.isEmpty() || model.length() > 64) {
            throw ApiException.badRequest("推荐模型不能为空");
        }
        template.setRecommendedModel(model);
        String size = template.getRecommendedSize() == null ? "" : template.getRecommendedSize().trim().toLowerCase(Locale.ROOT);
        if (!SIZE_PATTERN.matcher(size).matches()) {
            throw ApiException.badRequest("推荐尺寸格式应为 宽x高，如 896x1184");
        }
        template.setRecommendedSize(size);
        validateFieldsJson(template);
        Integer sortOrder = template.getSortOrder();
        if (sortOrder == null) {
            template.setSortOrder(0);
        } else if (sortOrder < 0 || sortOrder > 999) {
            throw ApiException.badRequest("显示顺序须在 0-999 之间");
        }
        Integer status = template.getStatus();
        if (status == null) {
            template.setStatus(1);
        } else if (status != 0 && status != 1) {
            throw ApiException.badRequest("状态仅支持 0(停用)/1(启用)");
        }
    }

    private void validateFieldsJson(ImageGenTemplate template) {
        String fieldsJson = template.getFieldsJson() == null ? "" : template.getFieldsJson().trim();
        if (fieldsJson.isEmpty() || fieldsJson.length() > 500) {
            throw ApiException.badRequest("字段定义 JSON 须为 1-500 字");
        }
        JsonNode node;
        try {
            node = objectMapper.readTree(fieldsJson);
        } catch (Exception exception) {
            throw ApiException.badRequest("字段定义不是合法 JSON");
        }
        if (!node.isArray() || node.isEmpty()) {
            throw ApiException.badRequest("字段定义须为非空 JSON 数组");
        }
        for (JsonNode item : node) {
            if (!item.isObject()
                    || !item.hasNonNull("key") || item.get("key").asText("").isBlank()
                    || !item.hasNonNull("label") || item.get("label").asText("").isBlank()) {
                throw ApiException.badRequest("字段定义每项须包含 key 与 label");
            }
        }
        template.setFieldsJson(fieldsJson);
    }
}
