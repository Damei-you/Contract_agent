package com.yy.agent.contract.service.document;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TikaDocumentTextExtractorTest {

    private final TikaDocumentTextExtractor extractor = new TikaDocumentTextExtractor();

    @Test
    void extractsTextFromUploadedPlainTextFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.txt",
                "text/plain",
                "甲方：上海甲方公司\n第一条 付款条件\n验收后30日付款。".getBytes(StandardCharsets.UTF_8)
        );

        ExtractedDocument document = extractor.extract(file);

        assertThat(document.filename()).isEqualTo("contract.txt");
        assertThat(document.contentType()).isEqualTo("text/plain");
        assertThat(document.detectedContentType()).isNotBlank();
        assertThat(document.text()).contains("甲方：上海甲方公司", "付款条件");
    }
}
