# 百度 OCR 图片转文档

## 功能

- 用户端：`/client/ocr`，登录用户上传单张 JPG、PNG、BMP 图片。
- 管理端：`/ocr`，管理员可以查看百度 OCR 配置状态并执行同样的转换。
- 后端：`POST /api/print/ocr/convert`，调用百度通用文字识别高精度接口 `accurate_basic`，生成 `.docx` 和/或 `.pdf`。
- 下载：生成结果保存在 `PRINTSHOP_UPLOAD_DIR/ocr/<ownerId>/`，下载接口仍要求 JWT，不暴露真实存储路径。

## 密钥配置

只在本地忽略的 env 文件或生产密钥管理系统中配置，不要写入源码、文档或提交记录：

```dotenv
BAIDU_OCR_API_KEY=<百度控制台的 API Key>
BAIDU_OCR_SECRET_KEY=<百度控制台的 Secret Key>
BAIDU_OCR_ENDPOINT=https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic
BAIDU_OCR_TIMEOUT_MS=60000
BAIDU_OCR_MAX_FILE_SIZE_MB=4
BAIDU_OCR_PDF_FONT_PATH=/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc
```

未配置 AK/SK 时服务仍可启动，但 OCR 转换接口返回 503，管理端状态页显示“待配置密钥”。

## 百度接口边界

当前实现按接口文档使用 OAuth 2.0 `client_credentials` 获取 `access_token`，再以 `application/x-www-form-urlencoded` 提交 Base64 图片和 `detect_direction=true`、`probability=true` 调用 `accurate_basic`。百度 OCR 返回逐行文本；本项目在本地生成 Word 和 PDF。PDF 不是把原图直接改扩展名，而是使用 OCR 文本生成可搜索文本 PDF。中文 PDF 依赖服务器上的 TrueType outline 中文字体（PDFBox 的 CIDFontType2 子集嵌入要求 glyf 表，OpenType/CFF 字体无法嵌入）：镜像除 `fonts-noto-cjk` 外额外安装 TrueType outline 的 `fonts-wqy-microhei`，字体查找顺序为 `BAIDU_OCR_PDF_FONT_PATH`（显式配置）→ 文泉驿微米黑/正黑 → Noto CJK → Windows 微软雅黑；`.ttc` 字体集合通过 FontBox `TrueTypeCollection` 解析并优先选择简体中文变体。所有候选字体都不可用时，PDF 转换返回明确的 503 错误提示，不再静默降级。Word 与 PDF 相互独立生成，单种格式失败不影响另一种格式和识别文本返回（失败信息在响应 `data.failedFormats` / `data.failureMessage` 中）。管理端状态接口的 `formats` 依据字体探针结果上报（深度校验 TTC 内含 glyf 字体并缓存），并提供 `pdfFontAvailable` 字段。

## 验证

```powershell
cd 打印\backend
mvnw.cmd -pl printshop -DskipTests compile
cd ..\printshop-web
npm run build
```

接入密钥后再进行真实图片联调，确认百度账户已开通对应 OCR 接口和额度。
