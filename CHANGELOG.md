# Changelog

所有重要变更均记录在此文件中。

## [0.2.0] - 2026-07-20

### 新增

- Phase 2 地质资料资源池及 `/documents` 管理页面
- PDF、Word、TXT、PNG/JPEG/TIFF 文件上传、扩展名校验和按年月分层存储
- 资料分页、全文条件搜索、区域/年份/类型/状态筛选接口
- 资料元数据编辑、处理状态切换、删除及鉴权预览/下载接口
- MyBatis Plus PostgreSQL 分页插件和 Phase 2 数据库迁移脚本
- 文件存储安全、格式限制及 API 序列化测试

### 安全

- 阻止目录穿越文件路径和不支持的上传格式
- API 响应隐藏服务器文件路径与创建者内部编号

### 修改

- Dashboard 与侧栏导航反映 Phase 2 当前进度
- Element Plus 控件颜色统一到地质工作台视觉体系

## [0.1.0] - 2026-07-20

### 新增

- Phase 1 Vue 3 + TypeScript 前端工程、登录页、路由、布局与 Dashboard
- Spring Boot 3 + Java 17 后端及 PostgreSQL/MyBatis Plus 数据访问基础
- 基于数据库用户和 BCrypt 密码的 JWT 登录接口
- Swagger/OpenAPI、统一响应结构与全局异常处理
- FastAPI AI 基础服务、配置管理、健康检查和自动接口文档
- `app_user`、`document`、`system_log` 数据库初始化脚本
- 前端、后端和 AI 服务的本地启动脚本

### 修改

- 建立项目根目录结构和开发环境说明
