# WMS 仓储管理系统

基于 Vue 3 + Element Plus + Spring Boot 的汽车零部件仓储管理系统，覆盖入库、出库、转包、库存追溯、AI 辅助管理等核心业务场景。

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + Element Plus + Vite + Axios |
| 后端 | Spring Boot 3.2.5 + Spring Security + JPA |
| 数据库 | H2（文件型，`backend/data/wmsdb`） |
| 认证 | JWT（jjwt 0.12.5）+ BCrypt 密码加密 |

## 快速启动

### 环境要求
- JDK 21+
- Maven 3.6+
- Node.js 18+

### 1. 启动后端
```bash
cd backend
mvn spring-boot:run
```
后端运行在 http://localhost:8080

### 2. 启动前端
```bash
cd frontend
npm install
npm run dev
```
前端运行在 http://localhost:3001

### 3. 登录
| 角色 | 账号 | 密码 | 说明 |
|------|------|------|------|
| 管理员 | admin | admin123 | 全部权限 |
| AI 管理员 | ai_admin | ai123456 | 全部权限 + AI 功能 |
| 普通用户 | 自行创建 | - | 仓库作业权限 |

## 功能模块

### 基础信息管理
- 供应商管理、客户管理、零件管理
- 仓库管理（容量监控）、库位管理、器具管理
- 支持批量导入（文本 + Excel）

### 入库管理
- 入库单创建/修改/作废，按供应商批量添加零件
- 自动生成看板（含二维码），打印与批量导出
- 扫码入库（摄像头 + 手动输入），支持部分入库和批量入库

### 出库管理
- 出库单管理（正常出库/退库/调账出库/调账退库）
- 带单出库：扫描出库看板 → FIFO 扣减库存
- 不带单出库：直接扫描库存看板出库

### 转包管理
- 转包单创建与管理
- 转包作业：扫描看板 → 选仓库/库位/器具 → 执行转包
- 转包结余追踪
- 支持扫描未入库/已入库/未出库看板转包

### 库存管理
- 库存总览、库存监控（按库位）、库存追溯、库存报表

### AI 仓库管理员
- 库存摘要、异常监控（缺货/高储/低储/超容）
- 智能问答：查询库存、看板追溯、流水记录
- 定时刷新异常数据

### 移动端
- 扫码入库、带单出库、不带单出库、封存/解封、转包作业、退库
- 适配手持 PDA 摄像头扫码

### 系统管理
- 用户管理、角色权限管理

## 项目结构

```
shixun/
├── backend/                     # Spring Boot 后端
│   ├── src/main/java/com/example/demo/
│   │   ├── config/              # Security、JWT、CORS、数据初始化
│   │   ├── controller/          # REST 控制器（入库/出库/转包/库存/AI/系统）
│   │   ├── entity/              # JPA 实体（23 张表）
│   │   ├── repository/          # JPA Repository
│   │   └── util/                # JWT 工具类
│   └── pom.xml
├── frontend/                    # Vue 3 前端
│   ├── src/
│   │   ├── api/                 # Axios 接口封装
│   │   ├── router/              # 路由 + 守卫
│   │   ├── views/               # 页面组件（40+ 个 .vue 文件）
│   │   │   ├── baseinfo/        # 基础信息
│   │   │   ├── inbound/         # 入库
│   │   │   ├── outbound/        # 出库
│   │   │   ├── operations/      # 转包
│   │   │   ├── inventory/       # 库存
│   │   │   ├── ai/              # AI 管理员
│   │   │   ├── mobile/          # 移动端
│   │   │   ├── children/        # 用户/角色管理
│   │   │   └── common/          # 公用组件
│   │   └── utils/               # Axios 封装
│   └── package.json
├── test.md                       # 开发日志
├── 需求分析文档.md               # 需求分析
├── 小组分工.md                   # 小组分工
└── 需求分析文档.md               # 需求分析
```

## 数据模型

| 实体 | 说明 | 编号规则 |
|------|------|----------|
| InboundOrder | 入库单 | RK-时间戳 |
| Kanban | 入库看板 | KB-时间戳-随机码 |
| OutboundOrder | 出库单 | CK-时间戳 |
| OutboundKanban | 出库看板 | OKB-时间戳-随机码 |
| RepackOrder | 转包单 | ZB-时间戳 |
| RepackKanban | 转包看板 | RKB-时间戳-随机码 |
| InventoryRecord | 库存流水 | 自动生成 |
| Part / Supplier / Customer / Warehouse / Location / Container | 基础资料 | 自定义编号 |

## 开发日志

详见 [test.md](test.md)
