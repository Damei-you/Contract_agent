package com.yy.agent.contractmvp.service.document;

import com.yy.agent.contractmvp.api.dto.ImportChunkDto;
import com.yy.agent.contractmvp.api.dto.ImportContractRequest;
import com.yy.agent.contractmvp.api.dto.ImportPolicyKnowledgeRequest;
import com.yy.agent.contractmvp.api.dto.PolicyKnowledgeItemDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 Tika 抽出的纯文本转换为现有导入接口可接受的草稿 DTO。
 * <p>
 * 这里只做确定性启发式解析，不能确认的主数据保持为空并返回 warning，由用户在前端确认后再导入。
 */
@Component
public class DocumentDraftParser {

    private static final int CONTRACT_CHUNK_MAX_LENGTH = 1_400;
    private static final int POLICY_CHUNK_MAX_LENGTH = 1_200;

    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "^(?:第[一二三四五六七八九十百千万0-9]+[章节条款]|[一二三四五六七八九十]+[、.．]|\\d+(?:\\.\\d+)*[、.．\\s]+).{0,120}$"
    );
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(19\\d{2}|20\\d{2})\\s*(?:年|[-/.])\\s*(\\d{1,2})\\s*(?:月|[-/.])\\s*(\\d{1,2})\\s*(?:日)?"
    );
    private static final Pattern MONEY_VALUE_PATTERN = Pattern.compile(
            "(?:人民币|RMB|CNY|￥|¥)?\\s*([0-9][0-9,]*(?:\\.\\d+)?)\\s*(万元|元)?"
    );
    private static final Pattern TAX_RATE_PATTERN = Pattern.compile(
            "税率[^0-9%]{0,20}([0-9]+(?:\\.\\d+)?)\\s*%"
    );
    private static final Pattern CONTRACT_ID_PATTERN = Pattern.compile(
            "(?:合同(?:编号|号)|编号)\\s*[:：]\\s*([A-Za-z0-9][A-Za-z0-9_.\\-/]{2,63})"
    );

    public ParsedContractDraft parseContractDraft(ExtractedDocument document) {
        String text = document.text();
        List<String> warnings = new ArrayList<>();
        List<ImportChunkDto> chunks = toContractChunks(text);
        if (chunks.isEmpty()) {
            chunks = List.of(new ImportChunkDto("CH-FILE-001", "", "全文", "", text));
            warnings.add("未识别到明显条款标题，已将全文作为一个条款块。");
        }

        String partyAName = firstNonBlank(
                findLineValue(text, "甲方", "买方", "采购方", "委托方"),
                ""
        );
        String partyBName = firstNonBlank(
                findLineValue(text, "乙方", "卖方", "供应方", "服务方", "受托方"),
                ""
        );
        if (partyAName.isBlank()) {
            warnings.add("未识别到甲方名称，请导入前补齐 partyAName。");
        }
        if (partyBName.isBlank()) {
            warnings.add("未识别到乙方名称，请导入前补齐 partyBName。");
        }

        BigDecimal amountIncTax = firstNonNull(
                findMoneyNear(text, "含税金额", "合同总金额", "合同金额", "总价", "价款", "合计"),
                null
        );
        BigDecimal taxRatePct = findTaxRate(text);
        BigDecimal amountExTax = firstNonNull(
                findMoneyNear(text, "不含税金额", "未税金额"),
                calculateAmountExTax(amountIncTax, taxRatePct)
        );
        if (amountIncTax == null) {
            warnings.add("未识别到合同金额，请导入前补齐 amountIncTax。");
        }
        if (taxRatePct == null) {
            warnings.add("未识别到税率，请导入前补齐 taxRatePct。");
        }
        if (amountExTax == null) {
            warnings.add("未识别到不含税金额，请导入前补齐 amountExTax。");
        }

        LocalDate signDate = findDateNear(text, "签订日期", "签署日期", "签约日期");
        LocalDate effectiveDate = firstNonNull(
                findDateNear(text, "生效日期", "生效日", "合同生效", "有效期自"),
                signDate
        );
        LocalDate endDate = findDateNear(text, "结束日期", "终止日期", "有效期至", "截止日期");
        if (signDate == null) {
            warnings.add("未识别到签订日期，请导入前补齐 signDate。");
        }
        if (effectiveDate == null) {
            warnings.add("未识别到生效日期，请导入前补齐 effectiveDate。");
        }
        if (endDate == null) {
            warnings.add("未识别到结束日期，请导入前补齐 endDate。");
        }

        String type = classifyContractType(text);
        String performanceSite = findLineValue(text, "履约地点", "交付地点", "服务地点", "项目地点");
        String paymentTermsSummary = findSentenceContaining(text, "付款", "支付", "结算");
        String notes = "由文件解析生成草稿，来源文件：" + document.filename() + "。请确认主数据后再导入。";
        String contractId = firstNonBlank(findContractId(text), stableContractId(text));

        ImportContractRequest draft = new ImportContractRequest(
                contractId,
                type,
                partyAName,
                partyBName,
                "CNY",
                amountExTax,
                taxRatePct,
                amountIncTax,
                signDate,
                effectiveDate,
                endDate,
                performanceSite,
                paymentTermsSummary,
                "",
                "MEDIUM",
                null,
                notes,
                chunks,
                false
        );
        return new ParsedContractDraft(draft, warnings);
    }

    public ParsedPolicyDraft parsePolicyDraft(ExtractedDocument document) {
        List<String> sections = dropLeadingPreamble(splitSections(document.text(), POLICY_CHUNK_MAX_LENGTH));
        if (sections.isEmpty()) {
            sections = List.of(document.text());
        }

        List<PolicyKnowledgeItemDto> policies = new ArrayList<>();
        String idBase = "POL-FILE-" + Integer.toUnsignedString(document.filename().hashCode(), 16).toUpperCase(Locale.ROOT);
        int seq = 0;
        for (String section : sections) {
            if (section.isBlank()) {
                continue;
            }
            seq++;
            String text = section.trim();
            policies.add(new PolicyKnowledgeItemDto(
                    idBase + "-" + String.format("%03d", seq),
                    classifyPolicyDomain(text),
                    classifyAppliesToContractType(text),
                    classifySeverity(text),
                    extractTriggerKeywords(text),
                    extractControlObjective(text),
                    text,
                    extractRequiredEvidence(text),
                    extractEscalationRole(text),
                    null,
                    null
            ));
        }

        List<String> warnings = new ArrayList<>();
        if (policies.isEmpty()) {
            warnings.add("未识别到可导入的制度条目。");
        }
        return new ParsedPolicyDraft(new ImportPolicyKnowledgeRequest(policies), warnings);
    }

    private static List<ImportChunkDto> toContractChunks(String text) {
        List<String> sections = dropLeadingPreamble(splitSections(text, CONTRACT_CHUNK_MAX_LENGTH));
        List<ImportChunkDto> chunks = new ArrayList<>();
        int seq = 0;
        for (String section : sections) {
            String trimmed = section.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            seq++;
            String firstLine = firstLine(trimmed);
            chunks.add(new ImportChunkDto(
                    "CH-FILE-" + String.format("%03d", seq),
                    "CH-" + String.format("%03d", seq),
                    extractTitle(firstLine, seq),
                    classifyClauseCategory(trimmed),
                    trimmed
            ));
        }
        return chunks;
    }

    private static List<String> dropLeadingPreamble(List<String> sections) {
        if (sections.size() <= 1) {
            return sections;
        }
        for (int i = 0; i < sections.size(); i++) {
            if (isHeading(firstLine(sections.get(i)))) {
                return i == 0 ? sections : sections.subList(i, sections.size());
            }
        }
        return sections;
    }

    private static List<String> splitSections(String text, int maxLength) {
        List<String> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawLine : text.split("\\n")) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            boolean heading = isHeading(line);
            if (heading && current.length() > 0) {
                addSplitSection(sections, current.toString(), maxLength);
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(line);
        }
        if (current.length() > 0) {
            addSplitSection(sections, current.toString(), maxLength);
        }
        if (sections.size() <= 1) {
            return splitByParagraphWindow(text, maxLength);
        }
        return sections;
    }

    private static List<String> splitByParagraphWindow(String text, int maxLength) {
        List<String> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawParagraph : text.split("\\n{2,}")) {
            String paragraph = rawParagraph.trim();
            if (paragraph.isBlank()) {
                continue;
            }
            if (current.length() > 0 && current.length() + paragraph.length() + 2 > maxLength) {
                sections.add(current.toString());
                current.setLength(0);
            }
            if (paragraph.length() > maxLength) {
                addSplitSection(sections, paragraph, maxLength);
                continue;
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(paragraph);
        }
        if (current.length() > 0) {
            sections.add(current.toString());
        }
        return sections;
    }

    private static void addSplitSection(List<String> sections, String section, int maxLength) {
        String trimmed = section.trim();
        if (trimmed.length() <= maxLength) {
            sections.add(trimmed);
            return;
        }
        int start = 0;
        while (start < trimmed.length()) {
            int end = Math.min(start + maxLength, trimmed.length());
            int boundary = findSentenceBoundary(trimmed, start, end);
            if (boundary > start + 200) {
                end = boundary + 1;
            }
            sections.add(trimmed.substring(start, end).trim());
            start = end;
        }
    }

    private static int findSentenceBoundary(String text, int start, int end) {
        for (int i = end - 1; i > start; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '；' || c == ';' || c == '.' || c == '!' || c == '！') {
                return i;
            }
        }
        return -1;
    }

    private static boolean isHeading(String line) {
        return line.length() <= 140 && HEADING_PATTERN.matcher(line).matches();
    }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline);
    }

    private static String extractTitle(String firstLine, int seq) {
        String title = firstLine
                .replaceFirst("^(第[一二三四五六七八九十百千万0-9]+[章节条款]|[一二三四五六七八九十]+[、.．]|\\d+(?:\\.\\d+)*[、.．\\s]+)\\s*", "")
                .trim();
        if (title.isBlank()) {
            return "条款 " + seq;
        }
        return title.length() > 80 ? title.substring(0, 80) : title;
    }

    private static String findLineValue(String text, String... labels) {
        for (String rawLine : text.split("\\n")) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            for (String label : labels) {
                int index = line.indexOf(label);
                if (index >= 0) {
                    String value = line.substring(index + label.length())
                            .replaceFirst("^[\\s:：、,，-]+", "")
                            .replaceAll("[（(].*?以下简称.*?[）)]", "")
                            .trim();
                    if (!value.isBlank()) {
                        return limit(value, 120);
                    }
                }
            }
        }
        return "";
    }

    private static BigDecimal findMoneyNear(String text, String... labels) {
        for (String label : labels) {
            int searchFrom = 0;
            while (searchFrom < text.length()) {
                int index = text.indexOf(label, searchFrom);
                if (index < 0) {
                    break;
                }
                searchFrom = index + label.length();
                if (shouldSkipLabelMatch(text, index, label)) {
                    continue;
                }
                String window = text.substring(index, Math.min(index + 120, text.length()));
                Matcher matcher = MONEY_VALUE_PATTERN.matcher(window);
                while (matcher.find()) {
                    BigDecimal amount = parseMoney(matcher.group(1), matcher.group(2));
                    if (amount != null) {
                        return amount;
                    }
                }
            }
        }
        return null;
    }

    private static boolean shouldSkipLabelMatch(String text, int index, String label) {
        if ("含税金额".equals(label) && index > 0) {
            char previous = text.charAt(index - 1);
            return previous == '不' || previous == '未';
        }
        return false;
    }

    private static BigDecimal parseMoney(String value, String unit) {
        try {
            BigDecimal amount = new BigDecimal(value.replace(",", ""));
            if ("万元".equals(unit)) {
                return amount.multiply(BigDecimal.valueOf(10_000L)).setScale(2, RoundingMode.HALF_UP);
            }
            return amount.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal findTaxRate(String text) {
        Matcher matcher = TAX_RATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group(1)).setScale(4, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal calculateAmountExTax(BigDecimal amountIncTax, BigDecimal taxRatePct) {
        if (amountIncTax == null || taxRatePct == null) {
            return null;
        }
        BigDecimal divisor = BigDecimal.ONE.add(taxRatePct.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
        return amountIncTax.divide(divisor, 2, RoundingMode.HALF_UP);
    }

    private static LocalDate findDateNear(String text, String... labels) {
        for (String label : labels) {
            int index = text.indexOf(label);
            if (index < 0) {
                continue;
            }
            LocalDate date = findFirstDate(text.substring(index, Math.min(index + 120, text.length())));
            if (date != null) {
                return date;
            }
        }
        return null;
    }

    private static LocalDate findFirstDate(String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return LocalDate.of(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))
            );
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String findContractId(String text) {
        Matcher matcher = CONTRACT_ID_PATTERN.matcher(text);
        if (!matcher.find()) {
            return "";
        }
        return sanitizeContractId(matcher.group(1));
    }

    private static String stableContractId(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", "");
        return "CTR-FILE-" + sha256Hex(normalized).substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private static String sanitizeContractId(String value) {
        String id = value == null ? "" : value.trim()
                .replaceAll("[^A-Za-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (id.length() > 64) {
            id = id.substring(0, 64);
        }
        return id;
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static String findSentenceContaining(String text, String... keywords) {
        for (String sentence : text.split("[。；;\\n]")) {
            String trimmed = sentence.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            for (String keyword : keywords) {
                if (trimmed.contains(keyword)) {
                    return limit(trimmed, 180);
                }
            }
        }
        return "";
    }

    private static String classifyContractType(String text) {
        int serviceScore = score(text, "服务", "运维", "咨询", "委托", "技术支持");
        int procurementScore = score(text, "采购", "供货", "设备", "货物", "交付");
        return serviceScore > procurementScore ? "service" : "procurement";
    }

    private static String classifyClauseCategory(String text) {
        if (containsAny(text, "付款", "支付", "结算", "发票", "税率", "金额")) {
            return "财务";
        }
        if (containsAny(text, "违约", "赔偿", "责任", "争议", "解除", "终止")) {
            return "法务";
        }
        if (containsAny(text, "保密", "合规", "关联", "廉洁", "审计")) {
            return "合规";
        }
        if (containsAny(text, "交付", "验收", "服务", "质量", "履约")) {
            return "业务";
        }
        return "";
    }

    private static String classifyPolicyDomain(String text) {
        if (containsAny(text, "付款", "支付", "发票", "税", "预算", "财务", "预付款")) {
            return "财务合规";
        }
        if (containsAny(text, "关联", "廉洁", "反舞弊", "合规", "利益冲突")) {
            return "合规审查";
        }
        if (containsAny(text, "法务", "违约", "争议", "诉讼", "仲裁")) {
            return "法务审查";
        }
        return "合同管理";
    }

    private static String classifyAppliesToContractType(String text) {
        int serviceScore = score(text, "服务", "运维", "咨询", "委托");
        int procurementScore = score(text, "采购", "供货", "货物", "设备");
        if (serviceScore > 0 && procurementScore > 0) {
            return "procurement;service";
        }
        return serviceScore > procurementScore ? "service" : "procurement";
    }

    private static String classifySeverity(String text) {
        if (containsAny(text, "不得", "严禁", "禁止", "必须", "超过", "关联交易", "预付款", "保函")) {
            return "HIGH";
        }
        if (containsAny(text, "应当", "需", "审批", "备案", "会签", "复核")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private static String extractTriggerKeywords(String text) {
        List<String> keywords = new ArrayList<>();
        for (String keyword : List.of("预付款", "保函", "发票", "税率", "关联交易", "违约", "验收", "付款", "审批", "会签")) {
            if (text.contains(keyword)) {
                keywords.add(keyword);
            }
        }
        return String.join(";", keywords);
    }

    private static String extractControlObjective(String text) {
        String sentence = findSentenceContaining(text, "控制", "确保", "防止", "规范", "要求", "不得", "应当", "必须");
        if (!sentence.isBlank()) {
            return limit(sentence, 120);
        }
        return limit(firstLine(text), 120);
    }

    private static String extractRequiredEvidence(String text) {
        String sentence = findSentenceContaining(text, "材料", "证明", "保函", "审批单", "附件", "凭证", "需提供", "应提供");
        return limit(sentence, 160);
    }

    private static String extractEscalationRole(String text) {
        for (String role : List.of("财务总监", "法务负责人", "合规负责人", "总经理", "部门负责人", "采购负责人", "业务负责人")) {
            if (text.contains(role)) {
                return role;
            }
        }
        return "";
    }

    private static int score(String text, String... keywords) {
        int score = 0;
        for (String keyword : keywords) {
            int index = text.indexOf(keyword);
            while (index >= 0) {
                score++;
                index = text.indexOf(keyword, index + keyword.length());
            }
        }
        return score;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    public record ParsedContractDraft(ImportContractRequest draft, List<String> warnings) {

        public ParsedContractDraft {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }

    public record ParsedPolicyDraft(ImportPolicyKnowledgeRequest draft, List<String> warnings) {

        public ParsedPolicyDraft {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }
}
