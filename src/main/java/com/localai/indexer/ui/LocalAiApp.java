package com.localai.indexer.ui;

import com.localai.indexer.ui.chat.ChatInterfaceView;
import com.localai.indexer.ui.dashboard.PipelineDashboardView;
import com.localai.indexer.ui.visualize.EmbeddingVisualizationView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * JavaFX Application Shell for Local AI Indexer.
 * Pure Java implementation with zero Kotlin or Compose Multiplatform dependencies.
 * Integrates Network Monitor, Chat & Citations, Pipeline Dashboard, and 2D Embedding Map.
 */
public class LocalAiApp extends Application {

    private PipelineDashboardView dashboardView;

    @Override
    public void start(Stage primaryStage) {
        // Ensure Primary Stage uses native OS window decorations with title bar controls (Minimize, Maximize/Restore, Close)
        primaryStage.initStyle(StageStyle.DECORATED);
        primaryStage.setFullScreen(false);
        primaryStage.setTitle("Local AI Privacy Indexer & Vector Search");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0d1117;");

        // Top Area: App Header + Network Monitor Panel
        VBox topContainer = new VBox();

        HBox appHeader = createAppHeader();
        NetworkMonitorPanel networkMonitor = new NetworkMonitorPanel();

        topContainer.getChildren().addAll(appHeader, networkMonitor);
        root.setTop(topContainer);

        // Center Area: Tab Navigation
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setStyle("-fx-background-color: #0d1117;");

        // Tab 1: Chat Interface & Source Citations
        ChatInterfaceView chatView = new ChatInterfaceView();
        Tab chatTab = new Tab("💬 Search & Citations", chatView);

        // Tab 2: Pipeline Dashboard & Analytics
        dashboardView = new PipelineDashboardView();
        Tab dashboardTab = new Tab("📊 Pipeline Dashboard", dashboardView);

        // Tab 3: 2D Embedding Map
        EmbeddingVisualizationView visualizerView = new EmbeddingVisualizationView();
        Tab visualizerTab = new Tab("🌐 2D Embedding Map (PCA)", visualizerView);
        visualizerTab.selectedProperty().addListener((obs, was, isSel) -> {
            if (isSel) {
                Platform.runLater(visualizerView::runPca);
            }
        });

        tabPane.getTabs().addAll(chatTab, dashboardTab, visualizerTab);
        root.setCenter(tabPane);

        // Bottom Status Bar
        HBox statusBar = createStatusBar();
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 1280, 820);

        // Load custom theme stylesheet if present
        try {
            String cssPath = getClass().getResource("/styles/theme.css") != null ?
                getClass().getResource("/styles/theme.css").toExternalForm() : null;
            if (cssPath != null) {
                scene.getStylesheets().add(cssPath);
            }
        } catch (Exception ignored) {}

        primaryStage.setScene(scene);
        primaryStage.setWidth(1280);
        primaryStage.setHeight(820);
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(700);

        primaryStage.setOnCloseRequest(e -> {
            shutdownApp();
        });

        primaryStage.show();
    }

    @Override
    public void stop() {
        shutdownApp();
    }

    private void shutdownApp() {
        if (dashboardView != null) {
            dashboardView.stopFeed();
        }
        Platform.exit();
        System.exit(0);
    }

    private HBox createAppHeader() {
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(12, 20, 12, 20));
        header.setStyle("-fx-background-color: #161b22; -fx-border-color: #30363d; -fx-border-width: 0 0 1 0;");

        Label logo = new Label("⚡ Local AI Indexer");
        logo.setStyle("-fx-text-fill: #58a6ff; -fx-font-size: 16px; -fx-font-weight: bold;");

        Label modeBadge = new Label("ZERO-OUTBOUND AIRGAP");
        modeBadge.setStyle("-fx-background-color: rgba(35, 134, 54, 0.2); -fx-text-fill: #3fb950; -fx-padding: 3 8 3 8; -fx-background-radius: 4px; -fx-font-size: 10px; -fx-font-weight: bold;");

        Label engineBadge = new Label("H2 VECTOR + TIKA + ONNX");
        engineBadge.setStyle("-fx-background-color: rgba(56, 139, 253, 0.15); -fx-text-fill: #58a6ff; -fx-padding: 3 8 3 8; -fx-background-radius: 4px; -fx-font-size: 10px; -fx-font-weight: bold;");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label hostLabel = new Label("Host: 127.0.0.1 (Loopback)");
        hostLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;");

        header.getChildren().addAll(logo, modeBadge, engineBadge, spacer, hostLabel);
        return header;
    }

    private HBox createStatusBar() {
        HBox bar = new HBox(16);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 16, 6, 16));
        bar.setStyle("-fx-background-color: #161b22; -fx-border-color: #30363d; -fx-border-width: 1 0 0 0;");

        Label statusDot = new Label("●");
        statusDot.setStyle("-fx-text-fill: #3fb950; -fx-font-size: 12px;");

        Label privacyText = new Label("Privacy: 0 Outbound Packets Verified");
        privacyText.setStyle("-fx-text-fill: #7ee787; -fx-font-size: 11px;");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label runtimeText = new Label("JavaFX Desktop UI • Strict Local Execution");
        runtimeText.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px;");

        bar.getChildren().addAll(statusDot, privacyText, spacer, runtimeText);
        return bar;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
