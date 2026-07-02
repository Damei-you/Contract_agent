package com.yy.agent.contract.service;

import com.yy.agent.contract.api.dto.ParseContractFileResponse;
import com.yy.agent.contract.api.dto.ParsePolicyKnowledgeFileResponse;
import com.yy.agent.contract.api.dto.ParsedFileMetadata;
import com.yy.agent.contract.service.document.DocumentDraftParser;
import com.yy.agent.contract.service.document.ExtractedDocument;
import com.yy.agent.contract.service.document.TikaDocumentTextExtractor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件解析用例：上传文件只转换为可编辑导入草稿，不直接落库。
 */
@Service
public class DocumentParseApplicationService {

    private final TikaDocumentTextExtractor textExtractor;
    private final DocumentDraftParser draftParser;

    public DocumentParseApplicationService(
            TikaDocumentTextExtractor textExtractor,
            DocumentDraftParser draftParser
    ) {
        this.textExtractor = textExtractor;
        this.draftParser = draftParser;
    }

    public ParseContractFileResponse parseContractFile(MultipartFile file) {
        ExtractedDocument document = textExtractor.extract(file);
        DocumentDraftParser.ParsedContractDraft parsed = draftParser.parseContractDraft(document);
        ParsedFileMetadata metadata = metadata(document, parsed.warnings());
        return new ParseContractFileResponse(
                metadata,
                parsed.draft(),
                parsed.draft().chunks().size()
        );
    }

    public ParsePolicyKnowledgeFileResponse parsePolicyKnowledgeFile(MultipartFile file) {
        ExtractedDocument document = textExtractor.extract(file);
        DocumentDraftParser.ParsedPolicyDraft parsed = draftParser.parsePolicyDraft(document);
        ParsedFileMetadata metadata = metadata(document, parsed.warnings());
        return new ParsePolicyKnowledgeFileResponse(
                metadata,
                parsed.draft(),
                parsed.draft().policies().size()
        );
    }

    private static ParsedFileMetadata metadata(ExtractedDocument document, List<String> warnings) {
        List<String> allWarnings = new ArrayList<>();
        if (document.text().length() >= 1_000_000) {
            allWarnings.add("抽取文本达到 1000000 字符上限，超出部分可能被截断。");
        }
        allWarnings.addAll(warnings);
        return new ParsedFileMetadata(
                document.filename(),
                document.contentType(),
                document.detectedContentType(),
                document.size(),
                document.text().length(),
                allWarnings
        );
    }
}
