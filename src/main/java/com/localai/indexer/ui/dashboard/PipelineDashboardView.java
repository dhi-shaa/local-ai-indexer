package com.localai.indexer.ui.dashboard;

import com.localai.indexer.model.PipelineMetricsDto;
import com.localai.indexer.model.PipelineStageTiming;
import com.localai.indexer.service.MockPipelineBackendService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Live dashboard for monitoring document ingestion throughput, embedding execution latency,
 * and dynamic per-stage timings (parse, chunk, embed, store).
 */
public class PipelineDashboardView extends ScrollPane {

    // Metric Value Labels
    private final Label throughputValLabel = new Label("0.0 docs/s");
    private final Label embeddingLatencyValLabel = new Label("0.0 ms");
    private final Label totalIndexedValLabel = new Label("0");
    private final Label memoryUsageValLabel = new Label("0.0 MB");
    private final Label currentDocLabel = new Label("Idle");

    // Stage Timing Labels & Progress Bars
    private final Label parseTimingLabel = new Label("0 ms");
    private final ProgressBar parseProgressBar = new ProgressBar(0);

    private final Label chunkTimingLabel = new Label("0 ms");
    private final ProgressBar chunkProgressBar = new ProgressBar(0);

    private final Label embedTimingLabel = new Label("0 ms");
    private final ProgressBar embedProgressBar = new ProgressBar(0);

    private final Label storeTimingLabel = new Label("0 ms");
    private final ProgressBar storeProgressBar = new ProgressBar(0);

    // Chart
    private XYChart.Series<String, Number> stageSeries;
    private BarChart<String, Number> stageBarChart;

    // Live Feed Controller
    private Timeline liveTimeline;
    private int simulationStep = 0;
    private boolean isRunning = false;
    private final Button toggleFeedBtn = new Button("▶ Start Live Feed");

    public PipelineDashboardView() {
        setFitToWidth(true);
        setStyle("-fx-background: #0d1117; -fx-background-color: #0d1117;");

        VBox content = new VBox(16);
        content.setPadding(new Insets(20));
        content.setStyle("-fx-background-color: #0d1117;");

        // 1. Top Header with Control Buttons
        HBox header = createHeader();

        // 2. Currently Processing Document Banner
        HBox docBanner = createDocBanner();

        // 3. Metric KPI Cards Grid
        GridPane kpiGrid = createKpiGrid();

        // 4. Per-Stage Timings Dynamic Progress Indicators
        VBox stageSection = createStageSection();

        // 5. Dynamic Latency Breakdown Bar Chart
        VBox chartSection = createChartSection();

        content.getChildren().addAll(header, docBanner, kpiGrid, stageSection, chartSection);
        setContent(content);

        setupLiveFeed();

        // Populate with initial frame
        updateMetrics(MockPipelineBackendService.getInstance().generateMetricsFrame(0));
    }

    private HBox createHeader() {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Pipeline Ingestion & Latency Analytics");
        title.setStyle("-fx-text-fill: #e6edf3; -fx-font-size: 16px; -fx-font-weight: bold;");

        Label badge = new Label("SPRING ENGINE TELEMETRY");
        badge.setStyle("-fx-background-color: rgba(35, 134, 54, 0.2); -fx-text-fill: #3fb950; -fx-padding: 3 8 3 8; -fx-background-radius: 4px; -fx-font-size: 10px; -fx-font-weight: bold;");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        toggleFeedBtn.setStyle("-fx-background-color: #238636; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 6 14 6 14; -fx-background-radius: 6px; -fx-cursor: hand;");
        toggleFeedBtn.setOnAction(e -> toggleLiveFeed());

        Button stepBtn = new Button("Step ⏭");
        stepBtn.setStyle("-fx-background-color: #21262d; -fx-text-fill: #c9d1d9; -fx-font-size: 11px; -fx-padding: 6 12 6 12; -fx-background-radius: 6px; -fx-border-color: #30363d; -fx-border-radius: 6px; -fx-cursor: hand;");
        stepBtn.setOnAction(e -> {
            simulationStep++;
            updateMetrics(MockPipelineBackendService.getInstance().generateMetricsFrame(simulationStep));
        });

        header.getChildren().addAll(title, badge, spacer, stepBtn, toggleFeedBtn);
        return header;
    }

    private HBox createDocBanner() {
        HBox banner = new HBox(8);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.setPadding(new Insets(8, 14, 8, 14));
        banner.setStyle("-fx-background-color: #161b22; -fx-background-radius: 6px; -fx-border-color: #30363d; -fx-border-radius: 6px;");

        Label icon = new Label("⚡ Ingestion Stream:");
        icon.setStyle("-fx-text-fill: #58a6ff; -fx-font-weight: bold; -fx-font-size: 11px;");

        currentDocLabel.setStyle("-fx-text-fill: #e6edf3; -fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;");

        banner.getChildren().addAll(icon, currentDocLabel);
        return banner;
    }

    private GridPane createKpiGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);

        grid.add(createKpiCard("INGESTION THROUGHPUT", throughputValLabel, "docs/sec rate", "#58a6ff"), 0, 0);
        grid.add(createKpiCard("EMBEDDING EXECUTION LATENCY", embeddingLatencyValLabel, "per-chunk inference ms", "#f0883e"), 1, 0);
        grid.add(createKpiCard("TOTAL CHUNKS INDEXED", totalIndexedValLabel, "embedded in H2 vector table", "#3fb950"), 2, 0);
        grid.add(createKpiCard("IN-MEMORY RAM USAGE", memoryUsageValLabel, "local runtime heap", "#a371f7"), 3, 0);

        return grid;
    }

    private VBox createKpiCard(String title, Label valLabel, String subtext, String accentColor) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(14));
        card.setStyle("-fx-background-color: #161b22; -fx-background-radius: 8px; -fx-border-color: #30363d; -fx-border-radius: 8px;");
        GridPane.setHgrow(card, Priority.ALWAYS);

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 10px; -fx-font-weight: bold;");

        valLabel.setStyle("-fx-text-fill: " + accentColor + "; -fx-font-size: 20px; -fx-font-weight: bold;");

        Label subLabel = new Label(subtext);
        subLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 10px;");

        card.getChildren().addAll(titleLabel, valLabel, subLabel);
        return card;
    }

    private VBox createStageSection() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(16));
        box.setStyle("-fx-background-color: #161b22; -fx-background-radius: 8px; -fx-border-color: #30363d; -fx-border-radius: 8px;");

        Label title = new Label("Per-Stage Execution Timings");
        title.setStyle("-fx-text-fill: #e6edf3; -fx-font-size: 13px; -fx-font-weight: bold;");

        box.getChildren().add(title);
        box.getChildren().add(new Separator());

        box.getChildren().add(createStageRow("1. Document Parsing (Apache Tika)", parseTimingLabel, parseProgressBar, "#388bfd"));
        box.getChildren().add(createStageRow("2. Semantic Token Chunking", chunkTimingLabel, chunkProgressBar, "#3fb950"));
        box.getChildren().add(createStageRow("3. Local Vector Embedding (ONNX)", embedTimingLabel, embedProgressBar, "#f0883e"));
        box.getChildren().add(createStageRow("4. H2 Vector Table Indexing", storeTimingLabel, storeProgressBar, "#a371f7"));

        return box;
    }

    private HBox createStageRow(String stageName, Label timingLabel, ProgressBar bar, String color) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(stageName);
        nameLabel.setPrefWidth(240);
        nameLabel.setStyle("-fx-text-fill: #c9d1d9; -fx-font-size: 12px;");

        bar.setPrefWidth(220);
        bar.setProgress(0.5);
        bar.setStyle("-fx-accent: " + color + ";");
        HBox.setHgrow(bar, Priority.ALWAYS);

        timingLabel.setPrefWidth(80);
        timingLabel.setAlignment(Pos.CENTER_RIGHT);
        timingLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px; -fx-font-weight: bold;");

        row.getChildren().addAll(nameLabel, bar, timingLabel);
        return row;
    }

    private VBox createChartSection() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(16));
        box.setStyle("-fx-background-color: #161b22; -fx-background-radius: 8px; -fx-border-color: #30363d; -fx-border-radius: 8px;");

        Label chartTitle = new Label("Stage Latency Breakdown (Milliseconds)");
        chartTitle.setStyle("-fx-text-fill: #e6edf3; -fx-font-size: 13px; -fx-font-weight: bold;");

        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Pipeline Stage");
        xAxis.setStyle("-fx-tick-label-fill: #8b949e; -fx-label-fill: #8b949e;");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Latency (ms)");
        yAxis.setStyle("-fx-tick-label-fill: #8b949e; -fx-label-fill: #8b949e;");

        stageBarChart = new BarChart<>(xAxis, yAxis);
        stageBarChart.setTitle(null);
        stageBarChart.setLegendVisible(false);
        stageBarChart.setPrefHeight(240);
        stageBarChart.setStyle("-fx-background-color: transparent;");

        stageSeries = new XYChart.Series<>();
        stageSeries.getData().add(new XYChart.Data<>("1. Parse", 20));
        stageSeries.getData().add(new XYChart.Data<>("2. Chunk", 12));
        stageSeries.getData().add(new XYChart.Data<>("3. Embed", 75));
        stageSeries.getData().add(new XYChart.Data<>("4. Store", 18));

        stageBarChart.getData().add(stageSeries);

        box.getChildren().addAll(chartTitle, stageBarChart);
        return box;
    }

    private void setupLiveFeed() {
        liveTimeline = new Timeline(new KeyFrame(Duration.millis(800), e -> {
            simulationStep++;
            PipelineMetricsDto metrics = MockPipelineBackendService.getInstance().generateMetricsFrame(simulationStep);
            updateMetrics(metrics);
        }));
        liveTimeline.setCycleCount(Timeline.INDEFINITE);
    }

    public void toggleLiveFeed() {
        isRunning = !isRunning;
        if (isRunning) {
            liveTimeline.play();
            toggleFeedBtn.setText("⏸ Pause Feed");
            toggleFeedBtn.setStyle("-fx-background-color: #d29922; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 6 14 6 14; -fx-background-radius: 6px; -fx-cursor: hand;");
        } else {
            liveTimeline.pause();
            toggleFeedBtn.setText("▶ Resume Feed");
            toggleFeedBtn.setStyle("-fx-background-color: #238636; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 6 14 6 14; -fx-background-radius: 6px; -fx-cursor: hand;");
        }
    }

    public void updateMetrics(PipelineMetricsDto metrics) {
        Platform.runLater(() -> {
            throughputValLabel.setText(String.format("%.1f docs/s", metrics.throughputDocsPerSec()));
            embeddingLatencyValLabel.setText(String.format("%.1f ms", metrics.embeddingLatencyMs()));
            totalIndexedValLabel.setText(String.format("%,d", metrics.totalChunksIndexed()));
            memoryUsageValLabel.setText(String.format("%.1f MB", metrics.memoryUsageMb()));
            currentDocLabel.setText(metrics.currentDocumentName());

            PipelineStageTiming timing = metrics.stageTiming();
            long total = Math.max(1, timing.totalLatencyMs());

            parseTimingLabel.setText(timing.parseLatencyMs() + " ms");
            parseProgressBar.setProgress((double) timing.parseLatencyMs() / total);

            chunkTimingLabel.setText(timing.chunkLatencyMs() + " ms");
            chunkProgressBar.setProgress((double) timing.chunkLatencyMs() / total);

            embedTimingLabel.setText(timing.embedLatencyMs() + " ms");
            embedProgressBar.setProgress((double) timing.embedLatencyMs() / total);

            storeTimingLabel.setText(timing.storeLatencyMs() + " ms");
            storeProgressBar.setProgress((double) timing.storeLatencyMs() / total);

            // Update chart bars
            if (stageSeries != null && stageSeries.getData().size() >= 4) {
                stageSeries.getData().get(0).setYValue(timing.parseLatencyMs());
                stageSeries.getData().get(1).setYValue(timing.chunkLatencyMs());
                stageSeries.getData().get(2).setYValue(timing.embedLatencyMs());
                stageSeries.getData().get(3).setYValue(timing.storeLatencyMs());
            }
        });
    }

    public void recordNewDocumentIngested(String fileName, int chunksCount, PipelineStageTiming timing) {
        Platform.runLater(() -> {
            int currentTotal = 0;
            try {
                currentTotal = Integer.parseInt(totalIndexedValLabel.getText().replace(",", ""));
            } catch (Exception ignored) {}
            int newTotal = currentTotal + chunksCount;
            totalIndexedValLabel.setText(String.format("%,d", newTotal));
            currentDocLabel.setText(fileName + " (Indexed " + chunksCount + " chunks)");

            if (timing != null) {
                long total = Math.max(1, timing.totalLatencyMs());

                parseTimingLabel.setText(timing.parseLatencyMs() + " ms");
                parseProgressBar.setProgress((double) timing.parseLatencyMs() / total);

                chunkTimingLabel.setText(timing.chunkLatencyMs() + " ms");
                chunkProgressBar.setProgress((double) timing.chunkLatencyMs() / total);

                embedTimingLabel.setText(timing.embedLatencyMs() + " ms");
                embedProgressBar.setProgress((double) timing.embedLatencyMs() / total);

                storeTimingLabel.setText(timing.storeLatencyMs() + " ms");
                storeProgressBar.setProgress((double) timing.storeLatencyMs() / total);

                if (stageSeries != null && stageSeries.getData().size() >= 4) {
                    stageSeries.getData().get(0).setYValue(timing.parseLatencyMs());
                    stageSeries.getData().get(1).setYValue(timing.chunkLatencyMs());
                    stageSeries.getData().get(2).setYValue(timing.embedLatencyMs());
                    stageSeries.getData().get(3).setYValue(timing.storeLatencyMs());
                }
            }
        });
    }

    public void stopFeed() {
        if (liveTimeline != null) {
            liveTimeline.stop();
        }
    }
}
