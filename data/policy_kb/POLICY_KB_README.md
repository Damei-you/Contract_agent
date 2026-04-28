# 政策/制度知识库（示例数据）

本目录用于存放“政策/制度知识库”维护稿与导入素材。

## 1. 你可以直接用于导入的文件

- `../vectorization/03_approval_policy_extended.csv`

该 CSV 与现有 `data/vectorization/03_approval_policy.csv` **同一字段结构**，只是在样例条目上更完整，可用于：

- 作为制度 RAG 的向量化输入（`policy_text_for_embedding`）
- 作为风险检查/审批辅助的“制度依据”候选（`policy_id`）
- 生成核对清单与升级路径（`required_evidence` / `escalation_role`）

## 2. 字段约定（与 CSV 表头一致）

- `policy_id`：制度条目 ID（建议稳定不变；如需更细颗粒度，可用 `POL-TAX-001#S1` 的形式表达“小节”）
- `policy_domain`：领域（财务合规/税务合规/会计核算/资金支付/采购履约/法务合规/服务履约等）
- `applies_to_contract_type`：适用合同类型（用 `;` 分隔，如 `采购合同;服务合同`）
- `severity`：严重度（高/中/低）
- `trigger_keywords`：触发关键词（建议用 `;` 分隔，便于关键词召回/高亮）
- `control_objective`：控制目标（短语/短句）
- `policy_text_for_embedding`：用于向量化的制度条文摘要（建议包含“触发条件 + 要求 + 例外/补救 + 证据”）
- `required_evidence`：要求提供的材料/证据（用 `;` 分隔，便于直接转 checklist）
- `escalation_role`：需要升级/会签的角色（如 财务负责人/税务岗/法务/信息安全）

## 3. 维护建议（面向 RAG）

- 结构：尽量写成“可引用的条款块”，避免一条 policy 过长导致引用不精确。
- 颗粒度：更建议把一个大的制度拆成 2-6 条 `policy_id#Sx`，从而让风险项能关联到更具体的依据。
- 可执行性：`required_evidence` 尽量具体化（例如“验收标准附件”“工时单模板”），让审批辅助输出可核验清单。

