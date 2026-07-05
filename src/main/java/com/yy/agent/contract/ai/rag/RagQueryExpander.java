package com.yy.agent.contract.ai.rag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 本地查询扩展：把业务人员的自然语言问题扩展为更适合 embedding 召回的场景词。
 * <p>
 * 这里只做确定性的轻量同义词/业务词补充，不调用大模型，保证检索评测可重复。
 */
final class RagQueryExpander {

    private RagQueryExpander() {
    }

    static String expand(String query, String fallback) {
        String normalized = (query == null || query.isBlank()) ? fallback : query.trim();
        Set<String> terms = new LinkedHashSet<>();
        terms.add(normalized);
        addIfMatches(terms, normalized,
                List.of("付款", "支付", "款", "账期", "尾款", "预付"),
                "付款条件 支付条件 资金计划 付款周期 对账确认");
        addIfMatches(terms, normalized,
                List.of("账户", "账号", "收款", "变更账户", "变更收款"),
                "收款账户 对公账户 账户变更 第三方代收 个人账户 反舞弊");
        addIfMatches(terms, normalized,
                List.of("发票", "税", "税率", "专票", "普票", "抵扣", "价税"),
                "增值税专用发票 发票类型 税率 开票信息 进项抵扣 价税分离 税率调整");
        addIfMatches(terms, normalized,
                List.of("验收", "交付", "质保", "里程碑", "对账", "SOW"),
                "验收合格 交付物清单 验收标准 里程碑 服务确认 对账单 质保起算");
        addIfMatches(terms, normalized,
                List.of("责任", "赔偿", "损失", "违约", "上限", "间接损失"),
                "责任上限 损失边界 直接损失 间接损失 违约责任 可保性");
        addIfMatches(terms, normalized,
                List.of("解除", "终止", "退出", "续期", "迟延"),
                "解除条件 终止退出 迟延交付 违约救济 资料移交");
        addIfMatches(terms, normalized,
                List.of("数据", "安全", "保密", "日志", "权限", "DPA", "删除", "导出", "备份"),
                "数据安全 数据处理附录 DPA 最小权限 访问审批 日志留存 数据导出 删除证明");
        addIfMatches(terms, normalized,
                List.of("分包", "外包", "驻场", "人员", "远程"),
                "分包限制 驻场人员 背景审查 入场培训 远程访问 连带责任");
        addIfMatches(terms, normalized,
                List.of("SLA", "可用性", "故障", "响应", "恢复", "服务积分"),
                "SLA 可用性指标 故障响应 恢复时限 服务积分 费用减免");
        return String.join(" ", terms);
    }

    private static void addIfMatches(Set<String> terms, String query, List<String> triggers, String expansion) {
        for (String trigger : triggers) {
            if (containsIgnoreCase(query, trigger)) {
                terms.add(expansion);
                return;
            }
        }
    }

    static List<String> signalTerms(String query) {
        String expanded = expand(query, "");
        List<String> terms = new ArrayList<>();
        for (String term : List.of(
                "付款", "支付", "发票", "税率", "专票", "验收", "交付", "质保", "责任", "赔偿",
                "间接损失", "解除", "终止", "违约", "收款", "账户", "SLA", "可用性", "故障",
                "数据", "安全", "日志", "权限", "分包", "驻场", "DPA", "删除", "导出",
                "里程碑", "对账", "尾款", "保密"
        )) {
            if (containsIgnoreCase(expanded, term)) {
                terms.add(term);
            }
        }
        return terms;
    }

    static boolean containsIgnoreCase(String text, String needle) {
        if (text == null || needle == null) {
            return false;
        }
        return text.toLowerCase().contains(needle.toLowerCase());
    }
}
