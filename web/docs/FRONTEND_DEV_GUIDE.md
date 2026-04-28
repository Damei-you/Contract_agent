# 前端开发文档（Vue 3 + TypeScript / Composition API）

基于 `docs/ARCHITECTURE_OVERVIEW.md` 与 `docs/API_REFERENCE.md`，本文档定义本项目前端需要完成的功能范围、推荐技术栈与工程落地方式，用于指导从 0 搭建一个可用的 Web UI 来调用后端合同 Agent 能力。

## 1. 目标与范围

### 1.1 目标

- 提供一个面向“合同审批/审阅人员”的 Web UI，用于：
  - 导入合同（主数据 + 条款分块 chunks）
  - 导入审批记录（全量替换）
  - 针对指定合同进行问答（RAG + LLM）
  - 进行风险检查（结构化风险结果）
  - 生成审批辅助建议与清单
- 降低接口调试成本：提供 JSON 粘贴/导入、结果展示、错误提示、可复制输出、历史记录。

### 1.2 明确不做（本期）

- 登录鉴权/权限系统（后端未提供相关接口）
- 合同列表、搜索、分页、导出等“业务后台”能力（后端未提供对应接口）
- 条款 chunk 的回查详情（后端仅返回 `retrievedChunkIds`，未提供查询 chunk 内容的 API）

### 1.3 关键边界约束（必须遵守）

- RAG 检索必须按 `contractId` 限定，前端在交互上也要始终“先选合同，再操作”。（见架构总览约束）
- `POST /api/contracts/import` 当前语义是“创建”，同一 `id` 重复导入会触发 409（不是幂等更新）。（见 API 文档）
- `qa / risk-check / approval-assist / approval-records/import` 都要求合同已存在；不存在会返回 404。（见 API 文档）

## 2. 后端接口清单（前端必须覆盖）

Base URL：`http://localhost:8088`  
路径前缀：`/api/contracts`

### 2.1 导入合同

- **POST** `/api/contracts/import`
- **用途**：导入合同主数据与条款分块 `chunks`，并触发检索所需数据准备
- **成功响应**：`{ "contractId": "..." }`
- **失败响应**：
  - 400：参数校验/JSON 错误
  - 409：合同 ID 已存在
  - 500：服务端异常

### 2.2 全量导入审批记录（替换）

- **POST** `/api/contracts/{id}/approval-records/import`
- **用途**：对指定合同全量替换审批历史（补录/迁移/初始化）
- **成功响应**：`{ "contractId": "...", "importedCount": 1 }`
- **失败响应**：400 / 404 / 500

### 2.3 合同问答

- **POST** `/api/contracts/{id}/qa`
- **请求体**：`{ "question": "..." }`
- **成功响应**：`{ "answer": "...", "retrievedChunkIds": ["c001"] }`
- **失败响应**：400 / 404 / 500

### 2.4 风险检查

- **POST** `/api/contracts/{id}/risk-check`
- **请求体**：无
- **成功响应**：`{ "summary": "...", "riskItems": [...] }`
- **失败响应**：404 / 500

### 2.5 审批辅助

- **POST** `/api/contracts/{id}/approval-assist`
- **请求体**：`{ "approverRole": "...", "focus": "..." }`
- **成功响应**：`{ "suggestion": "...", "checklist": ["..."] }`
- **失败响应**：400 / 404 / 500

## 3. 前端功能清单（页面/模块）

### 3.1 全局功能（所有页面通用）

- **环境配置**：
  - 后端端口默认 8088（见 `src/main/resources/application.yml`）
  - 建议本地通过 Vite 代理 `/api -> http://localhost:8088` 避免 CORS
- **错误提示与可观测性**：
  - 统一展示 HTTP 状态码（400/404/409/500）与“建议下一步操作”
  - 统一 loading 状态、禁用按钮、防重复提交
  - 当前未实现一键复制按钮（用户可手动选中复制）
- **历史记录**（Pinia 内存状态，当前未实现 localStorage 持久化）：
  - `currentContractId` 在 Pinia store 中跨页面共享（内存级别，刷新即丢失）
  - 导入页/审批记录导入页通过 ref 提供默认 JSON 模板，但未持久化上次导入的请求体
  - 提示：后续可扩展为 localStorage 持久化（参见 `contractContext.ts` 代码注释）

### 3.2 页面：合同导入 `/contracts/import`

- **当前实现（MVP 最小可用）**：
  - 使用 JSON 文本域（`<textarea>`）作为唯一的输入方式，提供一份预填的默认请求体
  - 用户可直接编辑 JSON 文本域内容，或粘贴自定义 JSON
  - 点击"提交导入"按钮触发 `POST /api/contracts/import`
  - 成功：展示 `contractId`，并自动写入 Pinia store（`contractContext.currentContractId`）
  - 409：通过统一错误提示展示"合同 ID 已存在"
  - loading 态：按钮禁用 + 文字提示（防重复提交）
- **与指南的差异说明（当前阶段未实现）**：
  - 未实现合同主数据表单字段（全部通过 JSON 文本域编辑）
  - 未实现 chunk 表格编辑器（直接在 JSON 中编辑 `chunks[]`）
  - 未实现"导出合同请求体为 JSON"复制按钮
  - 未实现导入成功后跳转到其他页面的快捷入口

### 3.3 页面：审批记录导入 `/contracts/:id/approval-records`

- **输入区域**：
  - 以 JSON 文本域为主（因为 `records[].riskItems[]/linkedPolicyIds[]/...` 层级较深）
  - 文本域预填了一份默认 JSON 模板可供编辑，未单独实现"生成模板"按钮
- **提交与结果**：
  - 先校验 `contractId` 必填，再 `JSON.parse` 解析文本域
  - 调用 `POST /api/contracts/{id}/approval-records/import`
  - 成功：展示 `importedCount`（JSON 格式化输出）
- **与指南的差异说明（当前阶段未实现）**：
  - 未实现独立的"生成模板"按钮（默认 JSON 直接写在源码中预填到文本域）
  - 未将导入请求体存储到本地历史（仅在页面内展示结果）

### 3.4 页面：合同问答 `/contracts/:id/qa`

- **输入区域**：
  - 合同 id 输入框（默认从 Pinia store 读取 `currentContractId`）
  - 问题文本域（默认预填示例问题）
  - 点击"发送问题"按钮触发请求（未实现回车提交）
- **结果展示**：
  - 展示 `answer`（纯文本）
  - 展示 `retrievedChunkIds`（JSON 格式化输出）
  - 说明：目前无法通过 API 回查 chunk 内容，因此只展示 chunkId
- **与指南的差异说明（当前阶段未实现）**：
  - 未实现回车提交
  - 未实现问题历史（本地保存最近 N 条）
  - 未实现 answer 一键复制按钮（用户可手动选择复制）
  - 未实现从导入请求体反向映射 chunkId -> 标题/类别

### 3.5 页面：风险检查 `/contracts/:id/risk`

- **操作**：点击按钮触发检查
- **结果展示**：
  - `summary`
  - `riskItems` 列表（字段：`code/severity/detail/relatedClauseChunkIds/relatedPolicyIds`）
  - `severity` 以颜色/徽标区分（LOW/MEDIUM/HIGH）

### 3.6 页面：审批辅助 `/contracts/:id/approval-assist`

- **输入区域**：
  - 合同 id 输入框（默认从 Pinia store 读取）
  - `approverRole` 文本输入框（默认"财务经理"，未实现下拉选择）
  - `focus` 文本输入框（默认"付款节点和发票合规"）
- **结果展示**：
  - `suggestion`（纯文本展示）
  - `checklist`（无序列表展示，未实现勾选式清单和一键复制功能）
- **与指南的差异说明（当前阶段未实现）**：
  - `approverRole` 未实现下拉选择 + 自定义输入（当前为纯文本输入框）
  - 未实现 checklist 勾选式清单和一键复制

## 4. 推荐技术栈（Vue 3 + TS）

### 4.1 工程与语言

- **Node.js**：建议 20+（团队统一即可）
- **包管理器**：`pnpm`
- **构建工具**：Vite
- **框架**：Vue 3（Composition API，`<script setup lang="ts">`）
- **类型**：TypeScript（建议开启严格模式）

### 4.2 路由、状态与请求

- **路由**：Vue Router
- **状态管理**：Pinia
  - UI 状态、历史记录、当前合同上下文使用 Pinia
  - 网络请求建议使用 composable（如 `useContractsApi`）封装
- **HTTP 客户端**：Axios
  - 全局超时建议 30s（API 文档建议 15~30s）
  - 统一错误归一化（按 400/404/409/5xx 输出用户可理解的提示）
  - 可选：加入轻量重试（仅网络错误与 5xx；最大 2~3 次，指数退避）

### 4.3 UI 与体验

- **组件库**：Element Plus（或 Naive UI，二选一即可）
- **JSON 编辑/格式化**：可选接入 monaco-editor / codemirror（也可先用 textarea + 格式化按钮）
- **日期处理**：dayjs（用于展示与输入处理）

### 4.4 工程质量

- **代码规范**：当前未配置 ESLint + Prettier（推荐后续加入）
- **单测**：当前未配置 Vitest + Vue Test Utils（推荐后续加入，重点测试：API 错误提示、表单校验、JSON 模板生成）
- **E2E（可选）**：Playwright（覆盖：导入->QA->风险检查->审批辅助的主链路）

## 5. 前端工程结构建议（新增子工程）

建议在仓库根目录新增 `web/`（或 `frontend/`）子目录承载前端工程，避免与后端 Maven 工程混杂。

### 5.1 目录结构示例

```
contract-agent-mvp/
  docs/
  src/                      # 后端
  web/                      # 前端（Vue 3 + TypeScript）
    .vscode/extensions.json
    index.html
    vite.config.ts
    package.json
    tsconfig.json
    tsconfig.app.json
    tsconfig.node.json
    src/
      main.ts
      App.vue
      style.css
      assets/
        hero.png
        vite.svg
        vue.svg
      components/         # 待扩展（当前为空目录）
      router/
        index.ts
      pages/
        ContractsImportPage.vue
        ContractQaPage.vue
        ContractRiskPage.vue
        ContractApprovalAssistPage.vue
        ContractApprovalRecordsImportPage.vue
      api/
        http.ts
        contracts.ts
      stores/
        contractContext.ts
      types/
        contracts.ts
      utils/
        json.ts
        errors.ts
```

### 5.2 路由建议

- `/contracts/import`
- `/contracts/:id/qa`
- `/contracts/:id/risk`
- `/contracts/:id/approval-assist`
- `/contracts/:id/approval-records`

## 6. TypeScript 数据模型（与 API 对齐）

### 6.1 合同导入 DTO

- `ContractImportRequest`
  - `id: string`
  - `type?: string`
  - `partyAName?: string`
  - `partyBName?: string`
  - `currency?: string`
  - `amountExTax?: number`
  - `taxRatePct?: number`
  - `amountIncTax?: number`
  - `signDate?: string`（`YYYY-MM-DD`）
  - `effectiveDate?: string`
  - `endDate?: string`
  - `performanceSite?: string`
  - `paymentTermsSummary?: string`
  - `businessOwnerDept?: string`
  - `riskTier?: "LOW" | "MEDIUM" | "HIGH" | string`
  - `vectorDocId?: string`
  - `notes?: string`
  - `chunks: Array<{ id: string; clauseCode?: string; clauseTitle?: string; clauseCategory?: string; textForEmbedding: string }>`

- `ContractImportResponse`
  - `contractId: string`

### 6.2 审批记录导入 DTO

- `ApprovalRecordsImportRequest`
  - `records: Array<{ id: string; stepNo?: number; approverRole?: string; decision?: string; decisionTime?: string; commentSummary?: string; linkedPolicyIds?: string[]; linkedClauseChunkIds?: string[]; riskItems?: Array<{ code: string; severity: string; detail: string; relatedClauseChunkIds?: string[]; relatedPolicyIds?: string[] }>; vectorDocId?: string }>`

- `ApprovalRecordsImportResponse`
  - `contractId: string`
  - `importedCount: number`

### 6.3 问答 DTO

- `ContractQaRequest`：`{ question: string }`
- `ContractQaResponse`：`{ answer: string; retrievedChunkIds: string[] }`

### 6.4 风险检查 DTO

- `ContractRiskCheckResponse`
  - `summary: string`
  - `riskItems: Array<{ code: string; severity: string; detail: string; relatedClauseChunkIds: string[]; relatedPolicyIds: string[] }>`

### 6.5 审批辅助 DTO

- `ApprovalAssistRequest`：`{ approverRole: string; focus: string }`
- `ApprovalAssistResponse`：`{ suggestion: string; checklist: string[] }`

## 7. 错误处理与重试（前端统一策略）

### 7.1 状态码处理建议

- **400**：提示“参数校验失败/JSON 格式错误”，引导用户检查必填字段与日期/金额类型
- **404**：提示“合同不存在”，引导用户先去“合同导入”页导入该 `contractId`
- **409**（仅导入合同常见）：提示“合同 ID 冲突”，引导更换 `id`
- **500/网络错误**：提示“服务异常或模型调用失败”，允许用户按重试策略重试

### 7.2 重试建议（与后端文档一致）

- 仅对 **网络抖动 / 5xx** 自动或半自动重试
- 最大重试 2~3 次；指数退避（如 500ms -> 1s -> 2s）
- 400/404/409 不做自动重试（先修复输入或状态）

## 8. 本地开发联调方式

### 8.1 启动后端

- 在仓库根目录运行：`mvn spring-boot:run`
- 默认端口：`8088`

### 8.2 启动前端（建议 Vite 代理）

- `web/` 中运行：`pnpm install && pnpm dev`
- `vite.config.ts` 配置代理：
  - `/api` -> `http://localhost:8088`
- 前端请求统一使用相对路径 `/api/...`，避免环境切换与 CORS 问题

## 9. 验收清单（Definition of Done）

- 能完成以下端到端流程并有清晰 UI 提示：
  - **导入合同**（成功/409/400）
  - **对已导入合同问答**（成功/404/400）
  - **风险检查**（成功/404）
  - **审批辅助**（成功/404/400）
  - **导入审批记录**（成功/404/400）
- 所有响应结果可复制（至少 answer / suggestion / summary / JSON）
- 发生错误时，能给出“下一步操作”提示（例如 404 -> 去导入合同）

