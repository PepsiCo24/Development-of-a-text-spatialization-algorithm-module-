# GeoText 接口说明

## 通用约定

- Spring Boot 业务接口前缀：`/api`
- FastAPI 智能服务接口前缀：`/api/v1`
- 除登录和健康检查外，业务接口必须携带 `Authorization: Bearer <token>`
- JSON 业务响应统一使用 `code`、`message`、`data`、`timestamp`

## 身份认证

### `POST /api/auth/login`

请求：

```json
{ "username": "admin", "password": "admin123" }
```

成功时返回 `accessToken`、`tokenType`、`displayName` 和 `role`。

## 资料资源池

### `GET /api/documents`

分页查询资料。可组合使用以下查询参数：

| 参数 | 说明 |
| --- | --- |
| `query` | 对名称、关键词和摘要进行模糊搜索 |
| `type` | `PDF`、`WORD`、`TXT` 或 `IMAGE` |
| `region` | 区域模糊匹配 |
| `year` | 资料年份 |
| `status` | `UPLOADED`、`PARSING`、`PARSED`、`FAILED` 或 `ARCHIVED` |
| `page` | 页码，从 1 开始 |
| `size` | 每页条数，最大 100 |

### `POST /api/documents`

使用 `multipart/form-data` 上传资料。必填字段为 `file`，可选元数据为 `name`、`region`、`year`、`keyword`、`summary`。

支持 PDF、DOC/DOCX、TXT、PNG、JPG/JPEG、TIF/TIFF，单文件最大 100 MB。服务端生成内部存储路径，不使用客户端文件名作为磁盘路径。

### `GET /api/documents/{id}`

获取单份资料元数据。响应不会包含服务器物理存储路径。

### `PUT /api/documents/{id}`

修改名称、区域、年份、关键词和摘要。

### `PATCH /api/documents/{id}/status`

请求示例：

```json
{ "status": "PARSING" }
```

### `GET /api/documents/{id}/preview`

返回原始文件流，默认 `Content-Disposition: inline`。传入 `disposition=attachment` 可触发下载。

### `DELETE /api/documents/{id}`

删除数据库记录和对应的服务器文件。

## 智能文档解析

### `POST /api/documents/{id}/parse`

创建异步解析任务，成功受理时返回 HTTP 202。业务服务读取受控存储中的原文件并发送给 AI 服务，不接受客户端提供的任意文件路径。已在解析中的资料不会重复创建任务。

### `GET /api/documents/{id}/parse/status`

返回资料当前的 `status`、`progress`、`pageCount`、`chunkCount`、`errorMessage` 和 `parsedAt`。状态在 `UPLOADED`、`PARSING`、`PARSED`、`FAILED` 间流转。

### `GET /api/documents/{id}/chunks`

按 `chunkIndex` 返回持久化文本块。每块包含章节标题、正文、起止页码和字符数，可用于原文追溯及后续实体识别。

### `POST /api/v1/documents/parse`

AI 服务的内部解析接口，使用 `multipart/form-data`，文件字段名为 `file`。支持 PDF、DOCX、TXT、PNG/JPEG/TIFF；旧版 `.doc` 需先转换为 DOCX。

响应示例：

```json
{
  "filename": "survey.pdf",
  "document_type": "PDF",
  "page_count": 12,
  "warnings": [],
  "chunks": [
    {
      "chunk_index": 0,
      "chapter_title": "第一章 区域地质概况",
      "content": "调查区位于……",
      "page_start": 1,
      "page_end": 2,
      "char_count": 1860
    }
  ]
}
```

## 健康检查

- Spring Boot：`GET /api/health`
- FastAPI：`GET /api/v1/health`

## 地质实体识别

### `POST /api/documents/{id}/entities/extract`

为已完成解析的资料创建异步实体识别任务，成功受理时返回 HTTP 202。请求体：

```json
{ "provider": "deepseek" }
```

`provider` 仅允许 `deepseek` 或 `qwen`。业务服务从 `document_chunk` 读取文本，不允许客户端替换原文或伪造来源页码。

### `GET /api/documents/{id}/entities/status`

返回 `PENDING`、`EXTRACTING`、`COMPLETED` 或 `FAILED` 状态，以及进度、实体数量、错误信息和完成时间。

### `GET /api/documents/{id}/entities`

返回资料实体列表。每个实体包含 `entityName`、`entityType`、`confidence`、`sourceText`、`page`、`chunkId`、`sourceStart`、`sourceEnd`、`provider` 和 `model`。

### `POST /api/v1/entities/extract`

AI 服务内部接口。输入资料编号、提供商和带数据库编号的文本块，逐块调用所选 LLM，并输出严格校验后的实体 JSON。支持的类型为：

`STRATUM`、`LITHOLOGY`、`ROCK_BODY`、`FAULT`、`MINERAL`、`ORE_BODY`、`MINERALIZATION_ZONE`、`GEOLOGICAL_AGE`、`PLACE`、`COORDINATE`、`GRADE`、`THICKNESS`、`DIP_DIRECTION`、`DIP_ANGLE`。

模型密钥仅通过 AI 服务的 `DEEPSEEK_API_KEY`、`QWEN_API_KEY` 环境变量提供，不通过请求或响应传输。

## 属性关系抽取与术语标准化

### `POST /api/documents/{id}/knowledge/extract`

对已完成实体识别的资料创建异步知识抽取任务。请求体仍使用 `{ "provider": "deepseek" }` 或 `qwen`。业务服务按文本块组织实体 ID、名称和类型，AI 服务只能引用这些 ID。

### `GET /api/documents/{id}/knowledge/status`

返回任务状态、进度、属性数、关系数、标准化匹配数、错误信息和完成时间。

### `GET /api/documents/{id}/knowledge`

一次返回标准化后的 `entities`、`attributes` 和 `relations`，用于构建关系视图和证据核查页面。

### `POST /api/v1/knowledge/extract`

AI 服务内部接口。属性类型限制为 `AGE`、`THICKNESS`、`SCALE`、`GRADE`、`LITHOLOGY`；关系类型限制为 `LOCATED_IN`、`OCCURS_IN`、`INTRUDES`、`CONTACTS`、`CONTROLS`、`CONTAINS`。未知实体 ID、自关联和非法类型会被拒绝。

### 地质词典接口

- `GET /api/dictionary?query=&type=`：按标准名称、别名和类型查询
- `POST /api/dictionary`：新增词典条目
- `PUT /api/dictionary/{id}`：编辑标准名称、别名、说明和启用状态
- `DELETE /api/dictionary/{id}`：删除词典条目

别名使用 `|` 分隔。实体标准化结果包括 `EXACT`（原名即标准名）、`ALIAS`（别名匹配）和 `UNMATCHED`（保留原名称）。
