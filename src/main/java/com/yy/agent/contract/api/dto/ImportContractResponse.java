package com.yy.agent.contract.api.dto;

/**
 * 导入合同响应：既覆盖成功导入结果，也覆盖“需要二次确认”的安全拦截结果。
 *
 * @param contractId           新建或指定的合同 id
 * @param imported             是否已实际写入
 * @param overwritten          是否覆盖了已有合同
 * @param requiresConfirmation 是否需要客户端二次确认覆盖
 * @param message              面向用户的结果说明
 */
public record ImportContractResponse(
        String contractId,
        boolean imported,
        boolean overwritten,
        boolean requiresConfirmation,
        String message
) {

    public static ImportContractResponse imported(String contractId, boolean overwritten) {
        String message = overwritten
                ? "合同已覆盖导入。"
                : "合同已导入。";
        return new ImportContractResponse(contractId, true, overwritten, false, message);
    }

    public static ImportContractResponse requiresOverwriteConfirmation(String contractId) {
        return new ImportContractResponse(
                contractId,
                false,
                false,
                true,
                "合同 ID 已存在。确认后将覆盖合同主数据和条款，并清空该合同审批记录。"
        );
    }
}
