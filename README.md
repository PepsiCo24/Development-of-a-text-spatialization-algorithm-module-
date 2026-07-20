# 基于填图对象智能识别的文本空间化算法模块

面向地质调查资料的智能文本空间化系统。系统将地质报告、区域调查资料和矿产调查资料转化为可检索、可追溯、可上图的实体、关系与空间对象。

## 当前进度

Phase 1–4 已实现：

- Vue 3 + TypeScript + Vite 前端，包含登录、路由守卫、响应式布局和系统工作台
- Spring Boot 3 + Java 17 后端，包含 PostgreSQL、MyBatis Plus、JWT 登录、Swagger、健康检查和统一异常响应
- FastAPI AI 服务，包含分层配置、健康检查、OpenAPI 文档和基础测试
- PostgreSQL 初始化脚本，创建 `app_user`、`document`、`system_log` 表及演示管理员
- 前端工作台主动检测后端与 AI 服务状态
- 地质报告、区域调查资料和矿产调查资料的上传、查询、分类、编辑、删除与状态管理
- PDF、Word、TXT、PNG/JPEG/TIFF 文件校验和安全的分层物理存储
- 名称、关键词、摘要、区域、年份、类型和处理状态组合检索与分页
- 鉴权文件预览/下载，服务端存储路径不会暴露给浏览器
- `/documents` 响应式资料资源池、上传表单、元数据编辑、状态切换和在线预览界面
- PDF、DOCX、TXT 与图片的正文提取；扫描 PDF 和图片通过 PaddleOCR 本地识别
- 标题/章节识别、文本清洗、按页码分块及 `document_chunk` 持久化
- Spring Boot 异步解析编排、进度与失败原因记录，以及 AI 服务 multipart 调用
- `/parsing` 任务队列与 `/documents/{id}/parse` 原件/解析文本对照页面
- DeepSeek 与 Qwen OpenAI-compatible API 的真实地质实体识别流程
- 地层、岩性、岩体、断裂、矿种、矿体、矿化带、地质年代、地名、坐标、品位、厚度、倾向、倾角共十四类实体
- 实体置信度、来源原文、页码、字符位置和调用模型持久化到 `entity` 表
- `/entities` 任务入口与原文颜色高亮、类型筛选、点击查看证据详情页面

后续将严格按 Phase 5–8 实现属性关系抽取、术语标准化、GIS 空间化、知识图谱、智能问答和成果导出。

## 系统架构

```text
Browser (Vue 3 / Element Plus / OpenLayers / ECharts)
                    │
        ┌───────────┴───────────┐
        │                       │
Spring Boot :8080          FastAPI :8000
业务接口 / 鉴权 / 数据       OCR / LLM / Embedding
        │                       │
PostgreSQL / Redis       Qdrant / Neo4j（后续阶段）
```

目录结构：

```text
frontend/       Vue 3 前端
backend/        Spring Boot 业务服务
ai-service/     FastAPI 智能服务
database/       数据库初始化与迁移脚本
docs/           设计、部署与接口文档
scripts/        本地启动脚本
```

## 环境要求

- Node.js 20+ 与 pnpm 9+
- Java 17 与 Maven 3.9+
- Python 3.11+
- PostgreSQL 14+

本项目按要求不使用 Docker。

## 本地运行

### 1. 初始化数据库

创建数据库后执行：

```powershell
psql -U postgres -d geotext -f database/init.sql
```

默认数据库连接为 `jdbc:postgresql://localhost:5432/geotext`，可通过 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 覆盖。生产环境必须设置新的 `JWT_SECRET`。

从 Phase 1 数据库升级时执行：

```powershell
psql -U postgres -d geotext -f database/migrations/V002__document_resource_pool.sql
psql -U postgres -d geotext -f database/migrations/V003__intelligent_document_parsing.sql
psql -U postgres -d geotext -f database/migrations/V004__geological_entity_recognition.sql
```

上传文件默认保存在 `uploads/documents/<年>/<月>/`，可用 `DOCUMENT_STORAGE_ROOT` 指定其他目录。支持 PDF、DOC/DOCX、TXT、PNG、JPG/JPEG、TIF/TIFF，单文件上限 100 MB。

### 2. 启动后端

```powershell
cd backend
mvn spring-boot:run
```

- API 健康检查：`http://localhost:8080/api/health`
- Swagger：`http://localhost:8080/swagger-ui.html`

### 3. 启动 AI 服务

```powershell
cd ai-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

PaddleOCR 与 PaddlePaddle 由 `requirements.txt` 安装，首次识别会下载所需模型。可通过 `OCR_LANGUAGE`（默认 `ch`）、`OCR_DEVICE`（默认 `cpu`）和 `PARSE_CHUNK_SIZE` 调整解析行为。DOCX 使用 `python-docx` 解析；旧版二进制 `.doc` 文件请先另存为 DOCX。

实体识别至少配置一个模型服务密钥：

```powershell
$env:DEEPSEEK_API_KEY="sk-..."
# 或
$env:QWEN_API_KEY="sk-..."
```

默认提供商由 `LLM_DEFAULT_PROVIDER=deepseek|qwen` 选择，也可在实体识别页面逐任务切换。API 地址、模型、温度、超时和最大输出长度均可通过 `.env.example` 中的变量覆盖；密钥只在 AI 服务环境中读取，不写入数据库或返回浏览器。

- 健康检查：`http://localhost:8000/api/v1/health`
- OpenAPI 文档：`http://localhost:8000/docs`

### 4. 启动前端

```powershell
cd frontend
pnpm install
pnpm dev
```

打开 `http://localhost:5173`，演示账号为 `admin / admin123`。

## 测试与构建

```powershell
cd frontend
pnpm build

cd ..\backend
mvn test

cd ..\ai-service
pytest
```

## 接口响应约定

后端业务接口统一返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": "2026-07-20T00:00:00Z"
}
```

详细接口以 Spring Boot Swagger 和 FastAPI `/docs` 为准。

Phase 1–4 接口说明见 [`docs/api.md`](docs/api.md)。
