package com.yy.agent.contractmvp.service.document;

/**
 * Tika 抽取后的文档文本与来源信息。
 *
 * @param filename            原始文件名
 * @param contentType         上传时声明的 MIME 类型
 * @param detectedContentType Tika 检测到的 MIME 类型
 * @param size                文件大小（字节）
 * @param text                规范化后的正文文本
 */
public record ExtractedDocument(
        String filename,
        String contentType,
        String detectedContentType,
        long size,
        String text
) {
}
