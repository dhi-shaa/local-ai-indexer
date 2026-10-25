package com.localai.indexer.ui.visualize;

import com.localai.indexer.model.DocumentChunkDto;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Interactive JavaFX Canvas rendering a 2D scatter plot of document chunks
 * reduced via Principal Component Analysis (PCA).
 * Supports pan, zoom, hover tooltips, click selection, and cluster color palettes.
 */
public class EmbeddingVisualizerCanvas extends StackPane {

    private final Canvas canvas;
    private final GraphicsContext gc;

    private List<DocumentChunkDto> chunks = new ArrayList<>();
    private DocumentChunkDto hoveredChunk = null;
    private DocumentChunkDto selectedChunk = null;

    // Viewport transformations
    private double zoom = 1.0;
    private double panX = 0.0;
    private double panY = 0.0;
    private double dragStartX;
    private double dragStartY;

    private double explainedVar1 = 0.0;
    private double explainedVar2 = 0.0;

    private Consumer<DocumentChunkDto> onChunkSelectedListener;

    // Category colors
    private final Map<String, Color> categoryColors = new HashMap<>();
    private final Color[] palette = {
        Color.web("#06b6d4"), // Cyan (Architecture)
        Color.web("#10b981"), // Emerald (Privacy/Security)
        Color.web("#8b5cf6"), // Violet (Database/Storage)
        Color.web("#f59e0b"), // Amber (UI/Visualization)
        Color.web("#ec4899"), // Rose (NLP/Embeddings)
        Color.web("#3b82f6")  // Blue (General)
    };

    private final Label infoHudLabel = new Label();
    private final Label varianceHudLabel = new Label();

    public EmbeddingVisualizerCanvas() {
        setStyle("-fx-background-color: #0d1117; -fx-border-color: #30363d; -fx-border-radius: 8px; -fx-background-radius: 8px;");

        canvas = new Canvas(800, 520);
        gc = canvas.getGraphicsContext2D();

        // Responsive resizing
        widthProperty().addListener((obs, oldW, newW) -> {
            canvas.setWidth(newW.doubleValue());
            render();
        });
        heightProperty().addListener((obs, oldH, newH) -> {
            canvas.setHeight(newH.doubleValue());
            render();
        });

        setupEventHandlers();

        // HUD Controls Bar
        HBox topBar = createControlBar();
        StackPane.setAlignment(topBar, Pos.TOP_RIGHT);
        StackPane.setMargin(topBar, new Insets(10));

        // Bottom Variance HUD
        VBox bottomHud = createHudBox();
        StackPane.setAlignment(bottomHud, Pos.BOTTOM_LEFT);
        StackPane.setMargin(bottomHud, new Insets(10));

        getChildren().addAll(canvas, topBar, bottomHud);
    }

    public void setOnChunkSelected(Consumer<DocumentChunkDto> listener) {
        this.onChunkSelectedListener = listener;
    }

    public void setChunks(List<DocumentChunkDto> newChunks, double var1, double var2) {
        this.chunks = new ArrayList<>(newChunks);
        this.explainedVar1 = var1;
        this.explainedVar2 = var2;

        // Assign colors by category
        int colorIdx = 0;
        for (DocumentChunkDto chunk : chunks) {
            String cat = chunk.category() != null ? chunk.category() : "General";
            if (!categoryColors.containsKey(cat)) {
                categoryColors.put(cat, palette[colorIdx % palette.length]);
                colorIdx++;
            }
        }

        updateHud();
        render();
    }

    public void resetView() {
        zoom = 1.0;
        panX = 0.0;
        panY = 0.0;
        render();
        updateHud();
    }

    private HBox createControlBar() {
        HBox controls = new HBox(8);
        controls.setAlignment(Pos.CENTER_RIGHT);
        controls.setPadding(new Insets(6, 12, 6, 12));
        controls.setStyle("-fx-background-color: rgba(22, 27, 34, 0.85); -fx-background-radius: 6px; -fx-border-color: #30363d; -fx-border-radius: 6px;");

        Button zoomInBtn = new Button("+ Zoom In");
        styleHudButton(zoomInBtn);
        zoomInBtn.setOnAction(e -> {
            zoom *= 1.25;
            render();
            updateHud();
        });

        Button zoomOutBtn = new Button("- Zoom Out");
        styleHudButton(zoomOutBtn);
        zoomOutBtn.setOnAction(e -> {
            zoom = Math.max(0.2, zoom / 1.25);
            render();
            updateHud();
        });

        Button resetBtn = new Button("⟲ Reset View");
        styleHudButton(resetBtn);
        resetBtn.setOnAction(e -> resetView());

        controls.getChildren().addAll(zoomInBtn, zoomOutBtn, resetBtn);
        return controls;
    }

    private VBox createHudBox() {
        VBox hud = new VBox(4);
        hud.setPadding(new Insets(8, 12, 8, 12));
        hud.setStyle("-fx-background-color: rgba(22, 27, 34, 0.85); -fx-background-radius: 6px; -fx-border-color: #30363d; -fx-border-radius: 6px;");

        infoHudLabel.setText("PCA 2D Vector Projection | 0 Points");
        infoHudLabel.setTextFill(Color.web("#e6edf3"));
        infoHudLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));

        varianceHudLabel.setText("Explained Variance: PC1: 0.0% | PC2: 0.0%");
        varianceHudLabel.setTextFill(Color.web("#8b949e"));
        varianceHudLabel.setFont(Font.font("Segoe UI", 11));

        hud.getChildren().addAll(infoHudLabel, varianceHudLabel);
        return hud;
    }

    private void styleHudButton(Button btn) {
        btn.setStyle("-fx-background-color: #21262d; -fx-text-fill: #c9d1d9; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-border-color: #30363d; -fx-border-radius: 4px;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #30363d; -fx-text-fill: #ffffff; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-border-color: #58a6ff; -fx-border-radius: 4px;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: #21262d; -fx-text-fill: #c9d1d9; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px; -fx-border-color: #30363d; -fx-border-radius: 4px;"));
    }

    private void updateHud() {
        infoHudLabel.setText(String.format("PCA 2D Vector Projection | %d Chunks | Zoom: %.1fx", chunks.size(), zoom));
        varianceHudLabel.setText(String.format("Explained Variance: PC1: %.1f%% | PC2: %.1f%% (Total: %.1f%%)",
            explainedVar1 * 100, explainedVar2 * 100, (explainedVar1 + explainedVar2) * 100));
    }

    private void setupEventHandlers() {
        canvas.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                dragStartX = e.getX() - panX;
                dragStartY = e.getY() - panY;
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                panX = e.getX() - dragStartX;
                panY = e.getY() - dragStartY;
                render();
            }
        });

        canvas.setOnScroll((ScrollEvent e) -> {
            double delta = e.getDeltaY();
            double factor = (delta > 0) ? 1.15 : (1.0 / 1.15);

            double mouseX = e.getX();
            double mouseY = e.getY();

            // Zoom centered on mouse
            panX = mouseX - (mouseX - panX) * factor;
            panY = mouseY - (mouseY - panY) * factor;
            zoom *= factor;

            render();
            updateHud();
            e.consume();
        });

        canvas.setOnMouseMoved(this::handleHover);

        canvas.setOnMouseClicked(e -> {
            if (hoveredChunk != null) {
                selectedChunk = hoveredChunk;
                if (onChunkSelectedListener != null) {
                    onChunkSelectedListener.accept(selectedChunk);
                }
                render();
            }
        });
    }

    private void handleHover(MouseEvent e) {
        double mouseX = e.getX();
        double mouseY = e.getY();

        DocumentChunkDto nearest = null;
        double minDistance = 16.0; // detection radius in pixels

        double w = canvas.getWidth();
        double h = canvas.getHeight();
        double cx = w / 2.0 + panX;
        double cy = h / 2.0 + panY;
        double scale = Math.min(w, h) * 0.42 * zoom;

        for (DocumentChunkDto chunk : chunks) {
            double px = cx + chunk.x2d() * scale;
            double py = cy + chunk.y2d() * scale;

            double dist = Math.hypot(px - mouseX, py - mouseY);
            if (dist < minDistance) {
                minDistance = dist;
                nearest = chunk;
            }
        }

        if (nearest != hoveredChunk) {
            hoveredChunk = nearest;
            render();
        }
    }

    public void render() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        gc.clearRect(0, 0, w, h);

        // 1. Background fill
        gc.setFill(Color.web("#0d1117"));
        gc.fillRect(0, 0, w, h);

        double cx = w / 2.0 + panX;
        double cy = h / 2.0 + panY;
        double scale = Math.min(w, h) * 0.42 * zoom;

        // 2. Grid lines
        renderGrid(w, h, cx, cy);

        // 3. Axes
        renderAxes(w, h, cx, cy);

        // 4. Scatter points
        renderPoints(cx, cy, scale);

        // 5. Tooltip for hovered chunk
        if (hoveredChunk != null) {
            renderTooltip(cx, cy, scale, hoveredChunk);
        }

        // 6. Cluster Legend
        renderLegend(w, h);
    }

    private void renderGrid(double w, double h, double cx, double cy) {
        gc.setStroke(Color.web("#161b22"));
        gc.setLineWidth(1.0);

        double step = 50 * zoom;
        if (step > 15) {
            for (double x = cx % step; x < w; x += step) {
                gc.strokeLine(x, 0, x, h);
            }
            for (double y = cy % step; y < h; y += step) {
                gc.strokeLine(0, y, w, y);
            }
        }
    }

    private void renderAxes(double w, double h, double cx, double cy) {
        // PC1 horizontal axis
        gc.setStroke(Color.web("#30363d"));
        gc.setLineWidth(1.5);
        if (cy >= 0 && cy <= h) {
            gc.strokeLine(0, cy, w, cy);
            gc.setFill(Color.web("#8b949e"));
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            gc.fillText("Principal Component 1 (PC1) →", w - 170, cy - 8);
        }

        // PC2 vertical axis
        if (cx >= 0 && cx <= w) {
            gc.strokeLine(cx, 0, cx, h);
            gc.setFill(Color.web("#8b949e"));
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            gc.fillText("↑ Principal Component 2 (PC2)", cx + 8, 20);
        }
    }

    private void renderPoints(double cx, double cy, double scale) {
        for (DocumentChunkDto chunk : chunks) {
            double px = cx + chunk.x2d() * scale;
            double py = cy + chunk.y2d() * scale;

            // Skip off-screen points
            if (px < -20 || px > canvas.getWidth() + 20 || py < -20 || py > canvas.getHeight() + 20) {
                continue;
            }

            Color pointColor = categoryColors.getOrDefault(chunk.category(), Color.web("#58a6ff"));
            boolean isHovered = (chunk == hoveredChunk);
            boolean isSelected = (chunk == selectedChunk);

            if (isSelected) {
                // Pulsing selected ring
                gc.setStroke(Color.web("#ffffff"));
                gc.setLineWidth(2.5);
                gc.strokeOval(px - 9, py - 9, 18, 18);
            }

            if (isHovered) {
                // Outer glowing halo
                gc.setFill(new Color(pointColor.getRed(), pointColor.getGreen(), pointColor.getBlue(), 0.35));
                gc.fillOval(px - 11, py - 11, 22, 22);

                gc.setStroke(Color.web("#ffffff"));
                gc.setLineWidth(1.5);
                gc.strokeOval(px - 6, py - 6, 12, 12);
            }

            // Core point circle
            double r = (isHovered || isSelected) ? 5.5 : 4.0;
            gc.setFill(pointColor);
            gc.fillOval(px - r, py - r, r * 2, r * 2);
        }
    }

    private void renderTooltip(double cx, double cy, double scale, DocumentChunkDto chunk) {
        double px = cx + chunk.x2d() * scale;
        double py = cy + chunk.y2d() * scale;

        double boxW = 260;
        double boxH = 80;
        double boxX = px + 12;
        double boxY = py - 40;

        // Keep inside canvas bounds
        if (boxX + boxW > canvas.getWidth() - 10) {
            boxX = px - boxW - 12;
        }
        if (boxY < 10) {
            boxY = 10;
        }
        if (boxY + boxH > canvas.getHeight() - 10) {
            boxY = canvas.getHeight() - boxH - 10;
        }

        // Tooltip box shadow & background
        gc.setFill(Color.web("rgba(22, 27, 34, 0.95)"));
        gc.setStroke(Color.web("#58a6ff"));
        gc.setLineWidth(1.2);
        gc.fillRoundRect(boxX, boxY, boxW, boxH, 8, 8);
        gc.strokeRoundRect(boxX, boxY, boxW, boxH, 8, 8);

        // Header: [ID] Category
        Color catColor = categoryColors.getOrDefault(chunk.category(), Color.web("#58a6ff"));
        gc.setFill(catColor);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        gc.fillText("[" + chunk.chunkId() + "] " + chunk.category(), boxX + 10, boxY + 20);

        // File & Page
        gc.setFill(Color.web("#e6edf3"));
        gc.setFont(Font.font("Segoe UI", 11));
        String fileText = chunk.fileName() + " (p. " + chunk.pageNumber() + ")";
        if (fileText.length() > 34) {
            fileText = fileText.substring(0, 31) + "...";
        }
        gc.fillText(fileText, boxX + 10, boxY + 38);

        // Snippet excerpt
        gc.setFill(Color.web("#8b949e"));
        gc.setFont(Font.font("Segoe UI", 10));
        String snippet = chunk.textSnippet();
        if (snippet.length() > 46) {
            snippet = snippet.substring(0, 43) + "...";
        }
        gc.fillText(snippet, boxX + 10, boxY + 54);

        gc.setFill(Color.web("#388bfd"));
        gc.fillText("Click to inspect chunk details", boxX + 10, boxY + 70);
    }

    private void renderLegend(double w, double h) {
        if (categoryColors.isEmpty()) return;

        double legX = 14;
        double legY = 14;
        double lineHeight = 18;
        double legH = categoryColors.size() * lineHeight + 16;
        double legW = 150;

        gc.setFill(Color.web("rgba(22, 27, 34, 0.85)"));
        gc.setStroke(Color.web("#30363d"));
        gc.setLineWidth(1.0);
        gc.fillRoundRect(legX, legY, legW, legH, 6, 6);
        gc.strokeRoundRect(legX, legY, legW, legH, 6, 6);

        double currY = legY + 16;
        gc.setFont(Font.font("Segoe UI", 11));
        for (Map.Entry<String, Color> entry : categoryColors.entrySet()) {
            gc.setFill(entry.getValue());
            gc.fillOval(legX + 8, currY - 8, 8, 8);

            gc.setFill(Color.web("#c9d1d9"));
            String catName = entry.getKey();
            if (catName.length() > 16) {
                catName = catName.substring(0, 14) + "..";
            }
            gc.fillText(catName, legX + 22, currY);
            currY += lineHeight;
        }
    }
}
