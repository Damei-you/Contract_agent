package com.yy.agent.contract.api.dto;

/**
 * 合同文件解析响应：解析结果先作为导入草稿返回，由用户确认后再调用现有导入接口落库。
 *
 * @param document   文件解析元数据
 * @param draft      可编辑的合同导入草稿
 * @param chunkCount 草稿中的条款块数量
 */
public record ParseContractFileResponse(
        ParsedFileMetadata document,
        ImportContractRequest draft,
        int chunkCount
) {
}
