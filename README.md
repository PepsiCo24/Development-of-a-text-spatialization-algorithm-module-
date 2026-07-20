# 基于填图对象智能识别的文本空间化算法模块

面向地质调查资料的智能文本空间化系统。系统将地质报告、区域调查资料和矿产调查资料转化为可检索、可追溯、可上图的实体、关系与空间对象。

## 当前进度

Phase 1–8 已全部实现：

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
- 年代、厚度、规模、品位、岩性五类属性的 LLM 抽取与证据持久化
- 位于、赋存于、侵入、接触、控制、包含六类实体关系的 LLM 抽取
- `entity_attribute`、`entity_relation`、`dictionary` 数据表和异步知识抽取流程
- 标准名称/别名词典管理，以及实体 `EXACT`、`ALIAS`、`UNMATCHED` 匹配结果
- `/knowledge` 属性关系核查入口、关系方向视图、属性卡片和术语标准化对照表
- `/dictionary` 地质词典查询、新增、编辑、启用和删除界面
- 地名、坐标、矿点、钻孔、断裂和调查区域六类空间信息抽取，并严格校验 Point、LineString、Polygon 几何
- `spatial_object` 表、PostGIS Geometry(4326) 持久化、空间索引及 V006 数据库迁移脚本
- OpenLayers 双底图、图层控制、缩放、图例、比例尺、坐标显示、距离与面积量算
- `/map` 空间化工作台，以及地图对象与来源资料、页码、原文证据的双向联动
- Neo4j 地层、岩体、构造、矿体、矿种和地区节点，以及位于、包含、控制和侵入关系
- 节点查询、1–3 层关系展开、最短路径查询和 ECharts Graph 交互可视化
- `BAAI/bge-m3` 文档块 Embedding 与 Qdrant 向量索引
- “向量检索 → Neo4j 上下文 → DeepSeek/Qwen”证据约束问答，回答包含实体、空间位置、资料和来源段落
- Excel 多工作表、CSV、JSON 与 GeoJSON 成果导出
- 用户新增/编辑/启停/删除、全流程任务监控和 AI 调用审计日志
- DeepSeek/Qwen API 地址、模型、Key、temperature 与 Prompt 模板持久化配置及实时应用
- 三份可直接导入的地质演示资料、批量导入脚本、部署文档、接口文档、系统截图和测试报告

当前版本为科研课题成果展示与专家评审用 `v1.0.0` 完整交付版。

## 系统架构

```text
Browser (Vue 3 / Element Plus / OpenLayers / ECharts)
                    │
        ┌───────────┴───────────┐
        │                       │
Spring Boot :8080          FastAPI :8000
业务接口 / 鉴权 / 数据       OCR / LLM / Embedding
        │                       │
PostgreSQL / PostGIS      Qdrant / Neo4j
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
- PostgreSQL 14+ 与 PostGIS 3+
- Neo4j 5+
- Qdrant 1.12+

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
psql -U postgres -d geotext -f database/migrations/V005__attributes_relations_dictionary.sql
psql -U postgres -d geotext -f database/migrations/V006__text_spatialization_gis.sql
psql -U postgres -d geotext -f database/migrations/V007__knowledge_graph_and_rag.sql
psql -U postgres -d geotext -f database/migrations/V008__system_management_and_llm_config.sql
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

空间化默认通过 `GEOCODING_BASE_URL` 调用地名解析服务，并使用 `GEOCODING_USER_AGENT` 标识请求；可设置 `GEOCODING_ENABLED=false` 禁用外部地名解析。文本中明确给出的坐标和几何优先使用，不会用地名解析结果覆盖。

### 4. 启动 Neo4j 与 Qdrant

本项目不使用 Docker。安装 Neo4j Community 后设置初始密码并启动 `neo4j console`；下载 Qdrant 对应平台的发行包后运行 `qdrant`（Windows 为 `qdrant.exe`）。默认连接为：

```powershell
$env:NEO4J_URI="bolt://localhost:7687"
$env:NEO4J_USERNAME="neo4j"
$env:NEO4J_PASSWORD="your-password"
$env:QDRANT_URL="http://localhost:6333"
$env:EMBEDDING_MODEL="BAAI/bge-m3"
```

首次构建图谱时 AI 服务会下载 BGE-M3 模型、创建 `geotext_chunks` 向量集合，并把资料实体/关系同步至 Neo4j。`QDRANT_API_KEY`、`QDRANT_COLLECTION` 和 `NEO4J_DATABASE` 可按部署环境覆盖。

- 健康检查：`http://localhost:8000/api/v1/health`
- OpenAPI 文档：`http://localhost:8000/docs`

### 5. 启动前端

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

## 交付资料

- [部署说明](docs/deployment.md)
- [接口说明](docs/api.md)
- [测试报告](docs/test-report.md)
- [Demo 数据](demo-data/README.md)
- 系统截图：[空间地图](docs/screenshots/spatial-map.png)、[知识图谱](docs/screenshots/knowledge-graph.png)、[成果与系统控制台](docs/screenshots/system-admin.png)
