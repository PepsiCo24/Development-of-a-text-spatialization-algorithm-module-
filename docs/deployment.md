# GeoText v1.0 部署说明

## 1. 运行环境

- Windows 10/11 或 Linux x86_64
- Node.js 20+、pnpm 9+
- Java 17、Maven 3.9+
- Python 3.11+
- PostgreSQL 14+、PostGIS 3+
- Neo4j Community 5+
- Qdrant 1.12+

系统按项目约束使用本机服务，不依赖 Docker。

## 2. 数据服务

1. 安装 PostgreSQL 与 PostGIS，创建数据库 `geotext`。
2. 新部署执行 `psql -U postgres -d geotext -f database/init.sql`。
3. 已有环境按顺序执行 `database/migrations/V002` 至 `V008`。
4. 安装 Neo4j Community，设置密码后运行 `neo4j console`。
5. 解压 Qdrant 发行包并运行 `qdrant`（Windows 为 `qdrant.exe`）。

生产环境建议分别为 PostgreSQL、Neo4j 和 Qdrant 创建专用服务账户，并限制 5432、7687、6333 端口只允许应用服务器访问。

## 3. AI 服务

```powershell
cd ai-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item .env.example .env
uvicorn app.main:app --host 127.0.0.1 --port 8000
```

必须修改 `.env` 中的 `NEO4J_PASSWORD`；若 Qdrant 开启鉴权，同时设置 `QDRANT_API_KEY`。首次向量索引会下载 `BAAI/bge-m3`，应预留模型缓存空间。DeepSeek/Qwen 密钥可放在 AI 服务环境变量中，也可由管理员在系统“模型配置”中保存并实时应用。

## 4. 后端服务

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/geotext"
$env:DB_USERNAME="geotext_app"
$env:DB_PASSWORD="strong-password"
$env:JWT_SECRET="replace-with-at-least-32-random-characters"
$env:AI_SERVICE_URL="http://127.0.0.1:8000"
cd backend
mvn clean package
java -jar target/geotext-backend-1.0.0.jar
```

上传文件默认写入 `uploads/documents`，可用 `DOCUMENT_STORAGE_ROOT` 指向独立数据盘。生产环境应定期备份 PostgreSQL、Neo4j、Qdrant 存储目录和上传目录。

## 5. 前端

```powershell
cd frontend
pnpm install --frozen-lockfile
pnpm build
```

将 `frontend/dist` 交由 Nginx、IIS 或静态服务器托管，并把 `/api` 反向代理至 `http://127.0.0.1:8080`。Vue Router 使用 history 模式，静态服务器需把未知路径回退到 `index.html`。

## 6. 验证

- 后端健康检查：`GET http://localhost:8080/api/health`
- AI 健康检查：`GET http://localhost:8000/api/v1/health`
- Swagger：`http://localhost:8080/swagger-ui.html`
- FastAPI OpenAPI：`http://localhost:8000/docs`
- 登录：`admin / admin123`（首次登录后应立即修改或新建管理员并停用演示口令）

运行 `scripts/import-demo-data.ps1` 可导入三份演示资料。随后依次在页面执行解析、实体识别、知识抽取、空间化和图谱同步。
