# 基于填图对象智能识别的文本空间化算法模块

面向地质调查资料的智能文本空间化系统。系统将地质报告、区域调查资料和矿产调查资料转化为可检索、可追溯、可上图的实体、关系与空间对象。

## 当前进度

Phase 1（基础工程架构）已实现：

- Vue 3 + TypeScript + Vite 前端，包含登录、路由守卫、响应式布局和系统工作台
- Spring Boot 3 + Java 17 后端，包含 PostgreSQL、MyBatis Plus、JWT 登录、Swagger、健康检查和统一异常响应
- FastAPI AI 服务，包含分层配置、健康检查、OpenAPI 文档和基础测试
- PostgreSQL 初始化脚本，创建 `app_user`、`document`、`system_log` 表及演示管理员
- 前端工作台主动检测后端与 AI 服务状态

后续将严格按 Phase 2–8 实现资料资源池、文档解析、实体关系抽取、术语标准化、GIS 空间化、知识图谱、智能问答和成果导出。

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

