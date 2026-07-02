package com.yy.agent.contract.api.dto;

import java.util.List;

/**
 * 文件解析元数据：用于前端展示本次解析的来源、大小和需要人工确认的提示。
 *
 * @param filename            原始文件名
 * @param contentType         浏览器上传时声明的 MIME 类型
 * @param detectedContentType Tika 检测到的 MIME 类型
 * @param size                文件大小（字节）
 * @param textLength          抽取后的文本长度
 * @param warnings            解析过程中的人工确认提示
 */
public record ParsedFileMetadata(
        String filename,
        String contentType,
        String detectedContentType,
        long size,
        int textLength,
        List<String> warnings
) {

    public ParsedFileMetadata {
        filename = filename == null ? "" : filename;
        contentType = contentType == null ? "" : contentType;
        detectedContentType = detectedContentType == null ? "" : detectedContentType;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
