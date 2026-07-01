package com.yy.agent.contractmvp.api.dto;

/**
 * 制度文件解析响应：解析结果先作为导入草稿返回，由用户确认后再调用现有导入接口落库。
 *
 * @param document    文件解析元数据
 * @param draft       可编辑的制度知识库导入草稿
 * @param policyCount 草稿中的制度条目数量
 */
public record ParsePolicyKnowledgeFileResponse(
        ParsedFileMetadata document,
        ImportPolicyKnowledgeRequest draft,
        int policyCount
) {
}
