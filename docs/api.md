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

## 健康检查

- Spring Boot：`GET /api/health`
- FastAPI：`GET /api/v1/health`
