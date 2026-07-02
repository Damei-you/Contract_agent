# Backend Rules

## Layering

- Controller 只做路由、参数校验入口和 HTTP 错误映射，不写业务判断。
- Application Service 负责业务编排、存在性校验、导入语义和跨仓储操作。
- Repository/Mapper 负责数据访问；业务规则不要下沉到 MyBatis XML 或 SQL 片段里。
- AI 编排逻辑放在 `AiContractAssistant` 及其下属 prompt/rag/agent 包，不散落到 Controller。

## API And DTO

- DTO 字段名必须和前端类型、API 文档保持一致。
- 请求 DTO 新增字段时要考虑向后兼容。布尔开关的默认值要清楚，例如 `includePolicyEvidence` 缺省为 `false`。
- 列表字段在响应中应 null-safe，优先返回空列表而不是 `null`。
- 资源不存在返回 404；重复/冲突语义要和业务规则一致；仓储或依赖不可用时返回明确 503。

## Import Semantics

- 合同导入同 `id` 首次重复提交只返回 `requiresConfirmation=true`，不写库。
- 合同覆盖必须由客户端带 `overwriteConfirmed=true` 明确确认。
- 覆盖合同会替换合同主数据和条款，并清空该合同审批记录；前端提示和后端语义必须一致。
- 制度导入按 `policyId` upsert，允许重复提交同一批数据用于补偿向量同步。

## Vector And Persistence

- 业务表是权威存储，`vector_store` 是派生索引。向量写入失败不能让已成功落库的制度导入被误判为失败。
- 向量写入使用业务派生 id 做 delete + add，保持重复导入幂等。
- 合同条款检索必须按 `contractId` 限定，禁止跨合同召回。
- 制度检索按 `docType=policy` 过滤；合同场景下再按合同类型收敛。

## Tests

- 行为变更优先补单元测试；跨层契约变更补应用服务或 Spring 上下文测试。
- DTO 兼容构造、null-safe 列表、默认布尔值需要测试守住。
- Docker/PostgreSQL smoke 测试可按环境跳过，但普通单测必须稳定通过。

## Comments

- Public API、DTO、复杂应用服务方法写简洁 Javadoc。
- 不要为显而易见的 getter/setter、简单赋值、直线流程写注释。
- 捕获异常后软降级时，注释说明为什么可以降级，以及客户端如何补偿。
