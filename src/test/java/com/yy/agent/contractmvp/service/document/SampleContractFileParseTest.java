package com.yy.agent.contractmvp.service.document;

import com.yy.agent.contractmvp.api.dto.ImportContractRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SampleContractFileParseTest {

    @Test
    void parsesGeneratedPdfSampleWithStableContractIdAndCorrectAmounts() throws Exception {
        Path sample = Path.of("data/import_samples/sample_procurement_contract.pdf");
        if (!Files.exists(sample)) {
            return;
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                sample.getFileName().toString(),
                "application/pdf",
                Files.readAllBytes(sample)
        );

        ExtractedDocument document = new TikaDocumentTextExtractor().extract(file);
        ImportContractRequest draft = new DocumentDraftParser().parseContractDraft(document).draft();

        assertThat(draft.id()).isEqualTo("CTR-PARSE-2026-001");
        assertThat(draft.amountIncTax()).isEqualByComparingTo(new BigDecimal("565000.00"));
        assertThat(draft.amountExTax()).isEqualByComparingTo(new BigDecimal("500000.00"));
        assertThat(draft.chunks()).hasSize(10);
    }
}
