package com.localai.indexer.ui;

import com.localai.indexer.model.DocumentChunkDto;
import com.localai.indexer.model.SourceCitation;
import com.localai.indexer.ui.chat.ChatInterfaceView;
import com.localai.indexer.ui.chat.SourceCitationPanel;
import com.localai.indexer.ui.dashboard.PipelineDashboardView;
import com.localai.indexer.ui.visualize.EmbeddingVisualizationView;
import com.localai.indexer.ui.visualize.EmbeddingVisualizerCanvas;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class UiComponentsSmokeTest {

    @BeforeAll
    public static void initJavaFx() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            // Already initialized
            latch.countDown();
        }
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX toolkit failed to initialize");
    }

    @Test
    public void testNetworkMonitorPanelInitialization() throws Exception {
        runOnFxThread(() -> {
            NetworkMonitorPanel panel = new NetworkMonitorPanel();
            assertNotNull(panel);
            panel.runAudit();
        });
    }

    @Test
    public void testChatInterfaceAndCitationPanel() throws Exception {
        runOnFxThread(() -> {
            SourceCitationPanel citationPanel = new SourceCitationPanel();
            assertNotNull(citationPanel);

            List<SourceCitation> citations = List.of(
                new SourceCitation("chk_test_01", "test.md", 1, 0.95, "Header 1", "Sample text excerpt")
            );
            citationPanel.displayCitations(citations);

            ChatInterfaceView chatView = new ChatInterfaceView();
            assertNotNull(chatView);
        });
    }

    @Test
    public void testPipelineDashboardView() throws Exception {
        runOnFxThread(() -> {
            PipelineDashboardView dashboard = new PipelineDashboardView();
            assertNotNull(dashboard);
            dashboard.stopFeed();
        });
    }

    @Test
    public void testEmbeddingVisualizerCanvasAndPca() throws Exception {
        runOnFxThread(() -> {
            EmbeddingVisualizerCanvas canvas = new EmbeddingVisualizerCanvas();
            assertNotNull(canvas);

            List<DocumentChunkDto> sample = List.of(
                new DocumentChunkDto("chk_1", "doc1.txt", 1, "Arch", "Text 1", new double[]{1.0, 2.0}, 0.2, -0.3),
                new DocumentChunkDto("chk_2", "doc2.txt", 2, "Security", "Text 2", new double[]{2.0, 1.0}, -0.4, 0.5)
            );
            canvas.setChunks(sample, 0.65, 0.25);
            canvas.render();

            EmbeddingVisualizationView view = new EmbeddingVisualizationView();
            assertNotNull(view);
        });
    }

    @Test
    public void testDocumentIngestionService() throws Exception {
        java.io.File sampleDoc = new java.io.File("src/test/resources/sample_privacy_doc.txt");
        assertTrue(sampleDoc.exists(), "Sample doc must exist for ingestion test");

        com.localai.indexer.service.DocumentIngestionService.IngestionResult result = 
            com.localai.indexer.service.DocumentIngestionService.getInstance().ingestFile(sampleDoc);
        assertTrue(result.success(), "Ingestion should succeed");
        assertTrue(result.chunksCount() > 0, "Chunks count should be > 0");
        assertNotNull(result.timing());
        assertEquals("sample_privacy_doc.txt", result.fileName());
    }

    @Test
    public void testChatInterfaceToastNotification() throws Exception {
        runOnFxThread(() -> {
            ChatInterfaceView chatView = new ChatInterfaceView();
            chatView.showToast("Document [sample.txt] successfully indexed (5 chunks created).");
            assertNotNull(chatView);
        });
    }

    private void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Action on JavaFX Application Thread timed out");
        if (failure.get() != null) {
            throw new AssertionError("Failure on JavaFX Application Thread", failure.get());
        }
    }
}
