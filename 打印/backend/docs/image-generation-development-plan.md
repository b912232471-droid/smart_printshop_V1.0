# 图片生成功能开发手册（已由迭代方案接管实施）

> **2026-09-06 状态更新**：本手册已实施完成。架构决策（编排层放 print-service、生成图落盘共享卷、网关超时调整）以《图文生成功能更新迭代方案-2026-09-06.md》第 2 节为准；第 2 节 photo-service 模块、第 4 节前端入口均已落地，存储与频控设计按方案第 5/7 节执行。仅第 1 节 M0 联调确认（size 参数格式、usage 返回结构）仍需真 Key 完成后回填。

## 现状与已就绪配置（2026-09-05 完成）

模型选型与配置已写入，功能代码尚未开发。已就绪：

- `photo-service/app/core/config.py`：`IMAGE_API_BASE_URL / IMAGE_API_KEY / IMAGE_API_TIMEOUT_SECONDS / IMAGE_MODEL`，以及 `IMAGE_MODEL_CATALOG`（三款模型含价格与场景备注）。
- `docker-compose.yml`：photo-service 透传 `IMAGE_*` 环境变量。
- `docker-compose.dev.yml`：本地开发将 `LOCAL_DEEPSEEK_API_KEY` 映射为 `IMAGE_API_KEY`（即 TokenHub 中转站 Key），`.env.local` 无需改动。
- `.env.example` + `scripts/generate_local_dev_env.py`：配置段与生成器已同步。
- 未配 Key 时接口应返回 503（对齐 `BAIDU_OCR_*` 模式），不伪造成功。

## 选定的三款模型（TokenHub，10 元/百万 tokens 按张折算）

| 模型 | 价格/张 | 定位 |
|---|---|---|
| `seedream-image-v5.0-lite` | 0.22 | 默认主力，中文文字渲染强，适合海报/手抄报 |
| `hy-image-v3` | 0.20 | 37 组预设尺寸贴合打印纸张比例，支持水印脚注 |
| `seedream-image-v5.0-pro` | 0.30（>261 万像素 0.60） | 旗舰质量档 |

`vidu-image-q2`（异步、唯一支持图片编辑）暂不入选，开发"图生图编辑"功能时再接入。价格为 2026-09 核实值，以控制台账单为准。

## 开发步骤

### 1. 联调确认 API 参数（前置，成本 < 1 元）

用真 Key 手动请求 `/v1/images/generations`，确认 `size` 取值格式、`response_format`（URL 还是 b64）、`usage` token 返回结构，并记入本文档。各模型专属指南见腾讯云 TokenHub 文档（图像生成模型调用概览：cloud.tencent.com/document/product/1823/135744）。

### 2. photo-service 后端模块（约 1 天）

- 新建 `app/services/image_generation_service.py`：客户端参照 `chat-service/app/deepseek.py`（urllib + settings）；Key 为空抛 503；模型 ID 白名单仅允许 `IMAGE_MODEL_CATALOG` 内的值。
- 新建 `app/api/v1/generate_image.py`：生成接口 + `GET /api/photo/image-models` 返回模型目录（前端下拉走接口，不硬编码）。
- `app/main.py` 注册 router；health/metrics 自动覆盖。
- 记录每次调用的 model、usage tokens、耗时（成本核算依据）。
- prompt 长度上限（建议 500 字符）。

### 3. 设计决策（开发前拍板）

- 生成图存储：直接回传前端（b64，最简）或挂共享 `data/files` 卷走 print-service 鉴权下载（可追溯）。
- 网关超时：本地 `GATEWAY_PHOTO_CB_TIMEOUT` 现为 30s，生成可能超时并被熔断，需调至 90s 或先只上 lite/hy 档。
- 每用户频控（建议 5 张/天，防刷）。
- 生成结果内容审核开关（校园平台合规），复用百度内容审核思路。

### 4. 前端入口（约 1 天）

用户端新页面（`/client` 下，图片区加 tab 或独立页）：prompt 输入 + 模型下拉（`image-models` 接口）+ 结果展示/下载；下载走 JWT blob，参照 `client/views/Ocr.vue` 的 `download` 实现。

### 5. 收尾（项目协作约定）

更新 `AGENTS.md`（9.1 条目）、新建 `docs/image-generation.md`、`.env.example` 有新参数时同步；构建验证 `python -m compileall app` + `npm run build` + 本地栈真实生成 1 张冒烟。

## 后续扩展

- `vidu-image-q2` 图生图编辑（异步任务模式，需任务表/轮询）。
- 多模型 A/B 与用户自选档位（catalog 已支持元数据扩展）。
