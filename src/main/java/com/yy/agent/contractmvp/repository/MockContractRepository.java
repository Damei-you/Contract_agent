package com.yy.agent.contractmvp.repository;

import com.yy.agent.contractmvp.domain.ApprovalDecision;
import com.yy.agent.contractmvp.domain.ApprovalRecord;
import com.yy.agent.contractmvp.domain.ClauseChunk;
import com.yy.agent.contractmvp.domain.Contract;
import com.yy.agent.contractmvp.domain.ContractType;
import com.yy.agent.contractmvp.domain.RiskItem;
import com.yy.agent.contractmvp.domain.RiskSeverity;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版合同仓储：启动时注入演示数据，支持并发读写（用于本地与集成测试）。
 * <p>
 * 三张表：合同主数据、按合同 id 分组的条款块、按合同 id 分组的审批记录。
 */
@Repository
public class MockContractRepository implements ContractRepository {

    private final Map<String, Contract> contracts = new ConcurrentHashMap<>();
    private final Map<String, List<ClauseChunk>> chunksByContract = new ConcurrentHashMap<>();
    private final Map<String, List<ApprovalRecord>> approvalsByContract = new ConcurrentHashMap<>();

    /** 构造时加载 {@link #seed()} 演示数据。 */
    public MockContractRepository() {
        seed();
    }

    /**
     * 写入两份示例合同（采购/服务）、条款块与示例审批轨迹，id 与 CSV 样例对齐便于联调。
     */
    private void seed() {
        String id1 = "CTR-2026-DEMO-001";
        contracts.put(id1, new Contract(
                id1,
                ContractType.PROCUREMENT,
                "某某科技有限公司",
                "某某设备供应商有限公司",
                "CNY",
                new BigDecimal("500000.00"),
                new BigDecimal("13"),
                new BigDecimal("565000.00"),
                LocalDate.of(2026, 1, 10),
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 12, 31),
                "甲方指定交付地点",
                "验收合格后30日内电汇支付至乙方账户",
                "信息技术部",
                RiskSeverity.MEDIUM,
                "doc_ctr_001",
                "框架下单分批交付"
        ));

        chunksByContract.put(id1, List.of(
                new ClauseChunk("c001", id1, "DEF", "定义与解释", "通用", "双方", RiskSeverity.LOW, "第1条",
                        "本合同中，“工作日”指中华人民共和国法定工作日；“验收”指甲方依据本合同及附件约定的标准对交付物进行检验并出具书面结论的行为。",
                        "", ""),
                new ClauseChunk("c002", id1, "SUB", "标的与规格", "标的", "乙方", RiskSeverity.MEDIUM, "第2条",
                        "乙方向甲方供应【产品/服务名称】，规格、数量、单价以双方确认的订单或附件《采购清单》为准。任何超出清单范围的增项须经甲方书面确认。",
                        "amount_ex_tax", "高"),
                new ClauseChunk("c003", id1, "PRICE", "价格与税费", "财务", "双方", RiskSeverity.HIGH, "第3条",
                        "合同价款为不含税价，增值税按适用税率另行计取；如遇国家税率政策调整，不含税价不变，税金按新政策执行。价格包含包装、运输、保险、安装调试（如适用）等乙方履行义务所需的全部费用。",
                        "amount_ex_tax", "高"),
                new ClauseChunk("c004", id1, "PAY", "付款条件", "财务", "甲方", RiskSeverity.HIGH, "第4条",
                        "甲方在收到乙方开具的合规增值税专用发票及双方签署的验收合格证明后【30】个自然日内付款。乙方账户信息以本合同约定为准，甲方不对错误账户付款承担责任。",
                        "amount_inc_tax", "高"),
                new ClauseChunk("c007", id1, "LIA", "责任限制", "风险", "双方", RiskSeverity.HIGH, "第7条",
                        "除因故意或重大过失、保密义务违反、知识产权侵权、人身损害等依法不可限制情形外，任何一方对本合同项下的间接损失、利润损失、商誉损失不承担责任；一方对另一方的累计赔偿责任以本合同总价款为上限。",
                        "amount_inc_tax", "高"),
                new ClauseChunk("c009", id1, "TERM", "合同终止", "履约", "双方", RiskSeverity.MEDIUM, "第9条",
                        "任何一方重大违约且在收到书面通知后【15】日内未补救的，守约方有权解除合同并要求违约方赔偿直接损失。合同解除不影响结算、保密、知识产权、争议解决等条款效力。",
                        "", "中")
        ));

        approvalsByContract.put(id1, List.of(
                new ApprovalRecord(
                        "AR-001",
                        id1,
                        3,
                        "财务经理",
                        ApprovalDecision.APPROVED,
                        OffsetDateTime.parse("2026-01-12T10:00:00+08:00"),
                        "付款节点与发票类型约定清晰，建议补充税率调整机制。",
                        List.of("POL-TAX-001"),
                        List.of("c004", "c003"),
                        List.of(new RiskItem("TAX_CLARITY", RiskSeverity.LOW, "已约定税率调整，保持即可", List.of(), List.of())),
                        "doc_ar_001"
                ),
                new ApprovalRecord(
                        "AR-002",
                        id1,
                        4,
                        "法务",
                        ApprovalDecision.CONDITIONAL_APPROVED,
                        OffsetDateTime.parse("2026-01-12T15:30:00+08:00"),
                        "责任上限与合同总价挂钩可接受；建议删除乙方单方解除权。",
                        List.of("POL-LEGAL-001"),
                        List.of("c007", "c009"),
                        List.of(new RiskItem("TERMINATION_ASYMMETRY", RiskSeverity.MEDIUM, "需对等化解除条件", List.of(), List.of())),
                        "doc_ar_002"
                )
        ));

        String id2 = "CTR-2026-DEMO-002";
        contracts.put(id2, new Contract(
                id2,
                ContractType.SERVICE,
                "某某科技有限公司",
                "某某信息技术服务有限公司",
                "CNY",
                new BigDecimal("1200000.00"),
                new BigDecimal("6"),
                new BigDecimal("1272000.00"),
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 5),
                LocalDate.of(2027, 2, 4),
                "远程+驻场结合",
                "按季度里程碑付款（4:3:3）",
                "财务部",
                RiskSeverity.HIGH,
                "doc_ctr_002",
                "含数据安全与外包人员管理条款"
        ));

        chunksByContract.put(id2, List.of(
                new ClauseChunk("c011", id2, "SCOPE", "服务范围与交付物", "标的", "乙方", RiskSeverity.MEDIUM, "第2条",
                        "乙方为甲方提供【系统运维/咨询服务】，具体工作内容、交付物、服务级别（SLA）以附件《工作说明书（SOW）》为准。未经甲方书面同意，乙方不得将核心服务整体转包。",
                        "amount_ex_tax", "高"),
                new ClauseChunk("c012", id2, "SEC", "信息安全与保密", "合规", "乙方", RiskSeverity.HIGH, "第6条",
                        "乙方在履行本合同过程中可能接触甲方数据与商业秘密，应遵守甲方信息安全制度，采取合理技术与组织措施，未经甲方授权不得向第三方披露或用于本合同以外目的。",
                        "", "高"),
                new ClauseChunk("c013", id2, "SUBP", "分包与驻场人员", "合规", "乙方", RiskSeverity.HIGH, "第7条",
                        "乙方驻场人员应通过甲方背景审查与入场培训；乙方对分包商的行为向甲方承担连带责任。人员变更须提前【5】个工作日通知并取得甲方同意。",
                        "", "高")
        ));

        approvalsByContract.put(id2, List.of(
                new ApprovalRecord(
                        "AR-003",
                        id2,
                        2,
                        "信息安全负责人",
                        ApprovalDecision.REJECTED,
                        OffsetDateTime.parse("2026-02-03T09:00:00+08:00"),
                        "缺少数据分级、出境与日志留存要求；驻场账号权限需最小化。",
                        List.of("POL-SVC-001"),
                        List.of("c012", "c013"),
                        List.of(new RiskItem("DATA_GOVERNANCE_GAP", RiskSeverity.HIGH, "需补充DPA/数据处理附录", List.of(), List.of())),
                        "doc_ar_003"
                )
        ));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Contract> findById(String id) {
        return Optional.ofNullable(contracts.get(id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ClauseChunk> findChunk(String contractId, String chunkId) {
        return findChunksByContractId(contractId).stream()
                .filter(c -> c.id().equals(chunkId))
                .findFirst();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ClauseChunk> findChunksByContractId(String contractId) {
        return List.copyOf(chunksByContract.getOrDefault(contractId, List.of()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ApprovalRecord> findApprovalRecordsByContractId(String contractId) {
        return List.copyOf(approvalsByContract.getOrDefault(contractId, List.of()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Contract save(Contract contract) {
        contracts.put(contract.id(), contract);
        return contract;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void replaceChunks(String contractId, List<ClauseChunk> chunks) {
        chunksByContract.put(contractId, new ArrayList<>(chunks));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void replaceApprovalRecords(String contractId, List<ApprovalRecord> records) {
        approvalsByContract.put(contractId, new ArrayList<>(records));
    }
}
