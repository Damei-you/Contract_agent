package com.yy.agent.contractmvp.ai.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class VectorBatchWriterTest {

    @Test
    void shouldSplitDocumentsIntoBatchesWhenExceedingBatchSize() {
        VectorStore vectorStore = mock(VectorStore.class);
        VectorBatchWriter writer = new VectorBatchWriter(vectorStore, 10);

        List<Document> documents = buildDocuments(15);

        writer.upsert(documents);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> deleteCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> addCaptor = ArgumentCaptor.forClass(List.class);

        InOrder inOrder = inOrder(vectorStore);
        // 第一批：先 delete 再 add，10 条
        inOrder.verify(vectorStore).delete(deleteCaptor.capture());
        inOrder.verify(vectorStore).add(addCaptor.capture());
        // 第二批：先 delete 再 add，5 条
        inOrder.verify(vectorStore).delete(deleteCaptor.capture());
        inOrder.verify(vectorStore).add(addCaptor.capture());
        inOrder.verifyNoMoreInteractions();

        assertThat(deleteCaptor.getAllValues()).hasSize(2);
        assertThat(deleteCaptor.getAllValues().get(0)).hasSize(10);
        assertThat(deleteCaptor.getAllValues().get(1)).hasSize(5);

        assertThat(addCaptor.getAllValues()).hasSize(2);
        assertThat(addCaptor.getAllValues().get(0)).hasSize(10);
        assertThat(addCaptor.getAllValues().get(1)).hasSize(5);
    }

    @Test
    void shouldCallSingleBatchWhenWithinBatchSize() {
        VectorStore vectorStore = mock(VectorStore.class);
        VectorBatchWriter writer = new VectorBatchWriter(vectorStore, 10);

        writer.upsert(buildDocuments(7));

        InOrder inOrder = inOrder(vectorStore);
        inOrder.verify(vectorStore).delete(anyList());
        inOrder.verify(vectorStore).add(anyList());
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void shouldDoNothingWhenInputIsEmpty() {
        VectorStore vectorStore = mock(VectorStore.class);
        VectorBatchWriter writer = new VectorBatchWriter(vectorStore, 10);

        writer.upsert(List.of());
        writer.upsert(null);

        verifyNoInteractions(vectorStore);
    }

    @Test
    void shouldFallbackToSizeOneWhenConfiguredBatchSizeIsInvalid() {
        VectorStore vectorStore = mock(VectorStore.class);
        VectorBatchWriter writer = new VectorBatchWriter(vectorStore, 0);

        writer.upsert(buildDocuments(3));

        // 0 兜底为 1：应当出现 3 次 delete + 3 次 add 调用
        InOrder inOrder = inOrder(vectorStore);
        for (int i = 0; i < 3; i++) {
            inOrder.verify(vectorStore).delete(anyList());
            inOrder.verify(vectorStore).add(anyList());
        }
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void shouldSwallowDeleteExceptionsAndContinueAdd() {
        VectorStore vectorStore = mock(VectorStore.class);
        // 模拟某些 VectorStore 实现在 id 不存在时抛异常的边界情况；upsert 仍应继续 add
        doThrow(new IllegalStateException("delete probe failed")).when(vectorStore).delete(anyList());
        VectorBatchWriter writer = new VectorBatchWriter(vectorStore, 10);

        writer.upsert(buildDocuments(2));

        // delete 抛了异常，但 add 仍然被调用，整体不向上抛
        InOrder inOrder = inOrder(vectorStore);
        inOrder.verify(vectorStore).delete(anyList());
        inOrder.verify(vectorStore).add(anyList());
    }

    @Test
    void shouldPropagateAddFailureSoCallerCanDegrade() {
        VectorStore vectorStore = mock(VectorStore.class);
        doThrow(new RuntimeException("HTTP 400 batch size invalid")).when(vectorStore).add(anyList());
        VectorBatchWriter writer = new VectorBatchWriter(vectorStore, 10);

        // 第一批 add 失败应直接向上抛，让上层应用服务能转换为 vectorIngestionWarning 软降级
        assertThatThrownBy(() -> writer.upsert(buildDocuments(15)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("HTTP 400 batch size invalid");
    }

    private static List<Document> buildDocuments(int n) {
        List<Document> docs = new ArrayList<>(n);
        IntStream.range(0, n).forEach(i ->
                docs.add(Document.builder()
                        .id("id-" + i)
                        .text("doc-" + i)
                        .build())
        );
        return docs;
    }
}
