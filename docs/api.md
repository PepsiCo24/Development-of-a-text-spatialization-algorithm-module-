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
