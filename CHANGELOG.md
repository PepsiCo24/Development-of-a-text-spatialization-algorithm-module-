# Changelog

所有重要变更均记录在此文件中。

## [0.5.0] - 2026-07-20

### 新增

- Phase 5 年代、厚度、规模、品位和岩性属性抽取
- 位于、赋存于、侵入、接触、控制和包含关系抽取
- 严格实体 ID 白名单验证，阻止模型生成的未知实体关系进入数据库
- `entity_attribute`、`entity_relation`、`dictionary` 表及 V005 数据库迁移脚本
- 地质词典查询、新增、编辑、启用和删除接口及管理页面
- 标准名、别名和未匹配三类术语标准化结果
- 知识任务入口、关系证据、属性卡片和术语对照核查页面
- 属性关系 AI 契约测试、后端编排测试和词典匹配测试

### 修改

- 重新进行实体识别时自动使旧知识抽取结果失效
- Dashboard、侧栏和三端版本更新为 Phase 5 / 0.5.0

## [0.4.0] - 2026-07-20

### 新增

- Phase 4 DeepSeek、Qwen OpenAI-compatible 地质实体识别服务
- 十四类地质实体的严格 JSON 输出校验、置信度归一化、去重与原文位置计算
- `entity` 表、文档实体任务状态字段及 Phase 4 数据库迁移脚本
- Spring Boot 异步实体识别编排、状态查询和实体结果接口
- `/entities` 任务入口、原文颜色高亮、类型筛选和实体证据详情页面
- DeepSeek/Qwen 请求契约测试与 Spring Boot 到 AI 服务的集成测试

### 修改

- 文档重新解析时自动使旧实体识别状态失效
- Dashboard 与侧栏导航更新为 Phase 4 状态
- 后端与 AI 服务版本更新为 0.4.0

## [0.3.0] - 2026-07-20

### 新增

- Phase 3 PDF、DOCX、TXT、图片及扫描 PDF 智能解析流水线
- 基于 PaddleOCR 3 的本地 OCR 服务、懒加载模型和扫描页回退识别
- 章节标题识别、文本清洗、分页分块与来源页码保留
- `document_chunk` 表、解析进度/错误字段及 Phase 3 数据库迁移脚本
- Spring Boot 异步解析编排、解析状态和文本块查询接口
- 智能解析任务队列及原件/解析文本双栏核查页面
- PDF、DOCX、TXT 和 OCR 回退解析测试，以及业务服务到 AI 服务的 multipart 集成测试

### 修改

- Dashboard 与侧栏导航更新为 Phase 3 状态
- 解析任务卡增加键盘访问和焦点样式
- 优化解析页面在窄屏设备上的布局

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
