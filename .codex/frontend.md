# Frontend Rules

## API Contract

- `web/src/types/contracts.ts` 必须和后端 DTO 保持同步。
- 页面不要手拼后端 URL，统一通过 `web/src/api/*` 封装。
- API 新增字段时，前端类型、请求体、页面默认值和文档要一起检查。

## Contract QA

- 合同问答默认不引用制度依据。`includePolicyEvidence` 的 UI 默认值必须是 `false`。
- 勾选“同时引用制度依据”时，请求体才传 `includePolicyEvidence: true`。
- 合同问答右侧证据面板默认展示合同条款证据；开启制度后展示合同条款与制度依据。

## AgentTrace UI

- 风险检查、审批辅助、合同问答、政策制度问答都使用共享证据面板展示可点击证据。
- 点击合同条款 id 调 `GET /api/contracts/{id}/chunks/{chunkId}`。
- 点击制度 id 调 `GET /api/policies/{policyId}`。
- 如果老接口只返回 `retrievedChunkIds` / `retrievedPolicyIds`，页面可以兜底构造可展示的 trace，但不要把兜底当成后端真实编排步骤。

## Layout And Interaction

- 业务工具页面优先做可用工作台，不做营销式 landing page。
- 页面左侧放输入和结果，右侧放证据/AgentTrace，保持风险检查、合同问答、审批辅助体验一致。
- 控件用明确的表单元素：checkbox 用于布尔选项，select 用于有限枚举，button 用于提交。
- 文案要反映真实行为，不要暗示未发生的检索或审查。

## Comments

- Vue 文件顶部可保留一句页面职责说明，不写长篇调用链。
- 复杂 computed、兜底兼容逻辑、交互边界可写短注释；普通模板结构不写注释。
- 删除过时注释，尤其是“当前后端不支持详情查询”这类会随功能变化失真的说明。
