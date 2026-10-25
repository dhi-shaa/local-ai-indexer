package com.localai.indexer.ui.visualize;

import com.localai.indexer.model.DocumentChunkDto;
import com.localai.indexer.service.MockPipelineBackendService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Main view for the 2D Embedding Map visualization.
 * Integrates the PCA reduction algorithm and the JavaFX Canvas scatter plot
 * with detail inspection capabilities.
 */
public class EmbeddingVisualizationView extends BorderPane {

    private final EmbeddingVisualizerCanvas canvas;
    private final VBox detailPanel;
    private final Label selectedChunkIdLabel = new Label("No chunk selected");
    private final Label selectedMetaLabel = new Label("Click any scatter point to inspect source metadata.");
    private final TextArea snippetArea = new TextArea();
    private final Label pcaStatusLabel = new Label();

    public EmbeddingVisualizationView() {
        setPadding(new Insets(16));
        setStyle("-fx-background-color: #0d1117;");

        // Top toolbar
        HBox toolbar = createToolbar();
        setTop(toolbar);
        BorderPane.setMargin(toolbar, new Insets(0, 0, 12, 0));

        // Center canvas
        canvas = new EmbeddingVisualizerCanvas();
        setCenter(canvas);

        // Right details panel
        detailPanel = createDetailPanel();
        setRight(detailPanel);
        BorderPane.setMargin(detailPanel, new Insets(0, 0, 0, 12));

        // Wire chunk selection to detail inspection
        canvas.setOnChunkSelected(this::showChunkDetails);

        // Initial PCA load
        runPca();

        // Listen for scene attachment to trigger render when shown
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                javafx.application.Platform.runLater(() -> {
                    if (canvas != null) {
                        canvas.render();
                    }
                });
            }
        });
    }

    private HBox createToolbar() {
        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10, 16, 10, 16));
        toolbar.setStyle("-fx-background-color: #161b22; -fx-background-radius: 8px; -fx-border-color: #30363d; -fx-border-radius: 8px;");

        Label title = new Label("2D High-Dimensional Vector Space Map");
        title.setStyle("-fx-text-fill: #e6edf3; -fx-font-size: 15px; -fx-font-weight: bold;");

        Label badge = new Label("PCA EIGEN-DECOMPOSITION");
        badge.setStyle("-fx-background-color: rgba(56, 139, 253, 0.15); -fx-text-fill: #58a6ff; -fx-padding: 3 8 3 8; -fx-background-radius: 4px; -fx-font-size: 10px; -fx-font-weight: bold;");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        pcaStatusLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px;");

        Button runPcaBtn = new Button("⚡ Recalculate PCA");
        runPcaBtn.setStyle("-fx-background-color: #238636; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 6px; -fx-padding: 6 12 6 12;");
        runPcaBtn.setOnAction(e -> runPca());

        Button resetBtn = new Button("⟲ Reset Zoom");
        resetBtn.setStyle("-fx-background-color: #21262d; -fx-text-fill: #c9d1d9; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 6px; -fx-border-color: #30363d; -fx-border-radius: 6px; -fx-padding: 6 12 6 12;");
        resetBtn.setOnAction(e -> canvas.resetView());

        toolbar.getChildren().addAll(title, badge, spacer, pcaStatusLabel, resetBtn, runPcaBtn);
        return toolbar;
    }

    private VBox createDetailPanel() {
        VBox panel = new VBox(10);
        panel.setPrefWidth(280);
        panel.setMinWidth(240);
        panel.setPadding(new Insets(14));
        panel.setStyle("-fx-background-color: #161b22; -fx-background-radius: 8px; -fx-border-color: #30363d; -fx-border-radius: 8px;");

        Label header = new Label("Chunk Inspector");
        header.setStyle("-fx-text-fill: #e6edf3; -fx-font-size: 13px; -fx-font-weight: bold;");

        selectedChunkIdLabel.setStyle("-fx-text-fill: #58a6ff; -fx-font-size: 12px; -fx-font-weight: bold;");
        selectedMetaLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px;");
        selectedMetaLabel.setWrapText(true);

        Label snippetHeader = new Label("Text Excerpt / Context:");
        snippetHeader.setStyle("-fx-text-fill: #c9d1d9; -fx-font-size: 11px; -fx-font-weight: bold;");

        snippetArea.setEditable(false);
        snippetArea.setWrapText(true);
        snippetArea.setPrefRowCount(10);
        snippetArea.setStyle("-fx-control-inner-background: #0d1117; -fx-text-fill: #e6edf3; -fx-font-family: 'Consolas', monospace; -fx-font-size: 11px; -fx-border-color: #30363d; -fx-border-radius: 4px;");
        VBox.setVgrow(snippetArea, Priority.ALWAYS);

        panel.getChildren().addAll(
            header,
            new Separator(),
            selectedChunkIdLabel,
            selectedMetaLabel,
            snippetHeader,
            snippetArea
        );

        return panel;
    }

    private void showChunkDetails(DocumentChunkDto chunk) {
        if (chunk == null) return;
        selectedChunkIdLabel.setText(chunk.chunkId() + " [" + chunk.category() + "]");
        selectedMetaLabel.setText("Source: " + chunk.fileName() + "\nPage: " + chunk.pageNumber() +
            "\nProjection: X=" + String.format("%.3f", chunk.x2d()) + ", Y=" + String.format("%.3f", chunk.y2d()));
        snippetArea.setText(chunk.textSnippet());
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (canvas != null) {
            canvas.render();
        }
    }

    public void runPca() {
        List<DocumentChunkDto> rawChunks = MockPipelineBackendService.getInstance().getIndexedChunks();
        PcaReducer.PcaResult result = PcaReducer.reduceTo2D(rawChunks);
        canvas.setChunks(result.projectedChunks(), result.explainedVarianceRatio1(), result.explainedVarianceRatio2());
        pcaStatusLabel.setText(String.format("PCA computed in %d ms | %d chunks projected", result.computeTimeMs(), rawChunks.size()));
        javafx.application.Platform.runLater(canvas::render);
    }
}
