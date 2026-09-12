package com.example.printshop.imagegen;

import com.example.printshop.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 绘图提示词 AI 润色（通用 OpenAI 兼容 chat 接口，当前指向 TokenHub 聚合平台的混元 hy3）。
 * 未配置 Key 时接口返回 503，前端隐藏润色按钮；润色失败由调用方降级为原始提示词。
 */
@Service
public class PromptPolishService {
    private static final Logger log = LoggerFactory.getLogger(PromptPolishService.class);
    private static final String SYSTEM_PROMPT =
            "你是海报、手抄报与绘画提示词专家。把用户的简单想法扩写成一段不超过400字的中文绘图提示词，"
                    + "补全画面主体、风格、构图、色彩与文字排版要求，只输出提示词本身，不要任何解释、引号或前后缀。";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final double temperature;
    private final int maxTokens;

    @Autowired
    public PromptPolishService(RestTemplate restTemplate,
                               ObjectMapper objectMapper,
                               @Value("${printshop.llm.api-key:}") String apiKey,
                               @Value("${printshop.llm.base-url:https://tokenhub.tencentmaas.com/v1}") String baseUrl,
                               @Value("${printshop.llm.model:hy3}") String model,
                               @Value("${printshop.llm.temperature:0.7}") double temperature,
                               @Value("${printshop.llm.max-tokens:2000}") int maxTokens) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl == null ? "https://tokenhub.tencentmaas.com/v1" : baseUrl.trim();
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public String polish(String prompt) {
        if (!isConfigured()) {
            throw ApiException.serviceUnavailable("提示词润色未配置，暂不可用");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("temperature", temperature);
        payload.put("max_tokens", maxTokens);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", prompt)
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/chat/completions", new HttpEntity<>(payload, headers), String.class);
            JsonNode content = objectMapper.readTree(response.getBody())
                    .path("choices").path(0).path("message").path("content");
            String polished = content.asText("").trim();
            if (polished.isEmpty()) {
                throw ApiException.serviceUnavailable("提示词润色失败，请直接使用原始描述生成");
            }
            return polished;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("llm polish failed: {}", exception.getMessage());
            throw ApiException.serviceUnavailable("提示词润色失败，请直接使用原始描述生成");
        }
    }
}
