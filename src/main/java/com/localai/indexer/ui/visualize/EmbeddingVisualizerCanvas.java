package com.localai.indexer.ui.visualize;

import com.localai.indexer.model.DocumentChunkDto;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
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
        Color.web("#f59e0b"), // Amber (UI/Visualization)
        Color.web("#8b5cf6"), // Violet (Database/Storage)
        Color.web("#10b981"), // Emerald (Privacy/Security)
        Color.web("#06b6d4"), // Cyan (Architecture)
        Color.web("#ec4899"), // Rose (NLP/Embeddings)
        Color.web("#3b82f6")  // Blue (General)
    };

    private final Label infoHudLabel = new Label();
    private final Label varianceHudLabel = new Label();
    private final VBox legendEntriesBox = new VBox(4);

    public EmbeddingVisualizerCanvas() {
        setStyle("-fx-background-color: #0d1117; -fx-border-color: #30363d; -fx-border-radius: 8px; -fx-background-radius: 8px;");

        setMinWidth(400);
        setMinHeight(300);
        setPrefSize(900, 600);

        canvas = new Canvas(900, 600);
        gc = canvas.getGraphicsContext2D();

        // Bind canvas dimensions directly to StackPane dimensions
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());

        // Trigger redraw whenever width or height changes
        canvas.widthProperty().addListener((obs, oldW, newW) -> render());
        canvas.heightProperty().addListener((obs, oldH, newH) -> render());

        setupEventHandlers();

        // HUD Controls Bar
        HBox topBar = createControlBar();
        StackPane.setAlignment(topBar, Pos.TOP_RIGHT);
        StackPane.setMargin(topBar, new Insets(10));

        // Bottom Variance HUD & Cluster Legend
        VBox bottomHud = createHudBox();
        StackPane.setAlignment(bottomHud, Pos.BOTTOM_LEFT);
        StackPane.setMargin(bottomHud, new Insets(10));

        getChildren().addAll(canvas, topBar, bottomHud);
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        render();
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
        controls.setStyle("-fx-background-color: rgba(22, 27, 34, 0.92); -fx-background-radius: 6px; -fx-border-color: #30363d; -fx-border-radius: 6px;");

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
        VBox hud = new VBox(6);
        hud.setPadding(new Insets(10, 14, 10, 14));
        hud.setStyle("-fx-background-color: rgba(22, 27, 34, 0.95); -fx-background-radius: 8px; -fx-border-color: #30363d; -fx-border-radius: 8px;");

        infoHudLabel.setText("PCA 2D Vector Projection | 0 Points");
        infoHudLabel.setTextFill(Color.web("#f0f6fc"));
        infoHudLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));

        varianceHudLabel.setText("Explained Variance: PC1: 0.0% | PC2: 0.0%");
        varianceHudLabel.setTextFill(Color.web("#c9d1d9"));
        varianceHudLabel.setFont(Font.font("Segoe UI", 11));

        // Legend Section located below the HUD metrics
        Label legendHeader = new Label("Cluster Categories:");
        legendHeader.setTextFill(Color.web("#8b949e"));
        legendHeader.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));

        legendEntriesBox.setSpacing(4);

        hud.getChildren().addAll(infoHudLabel, varianceHudLabel, new Separator(), legendHeader, legendEntriesBox);
        return hud;
    }

    private void updateLegend() {
        legendEntriesBox.getChildren().clear();
        for (Map.Entry<String, Color> entry : categoryColors.entrySet()) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);

            Circle dot = new Circle(5, entry.getValue());
            dot.setStroke(Color.web("#ffffff", 0.7));
            dot.setStrokeWidth(1.0);

            Label nameLabel = new Label(entry.getKey());
            nameLabel.setTextFill(Color.web("#f0f6fc")); // High contrast label color
            nameLabel.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11));

            row.getChildren().addAll(dot, nameLabel);
            legendEntriesBox.getChildren().add(row);
        }
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
        updateLegend();
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
        double minDistance = 18.0; // detection radius in pixels

        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;
        double scaleFactor = Math.min(w, h) * 0.4;

        for (DocumentChunkDto chunk : chunks) {
            double screenX = (w / 2.0) + (chunk.x2d() * scaleFactor * zoom) + panX;
            double screenY = (h / 2.0) - (chunk.y2d() * scaleFactor * zoom) + panY;

            double dist = Math.hypot(screenX - mouseX, screenY - mouseY);
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

        // 2. Grid lines
        renderGrid(w, h);

        // 3. Axes
        renderAxes(w, h);

        // 4. Scatter points with coordinate transformation
        renderPoints(w, h);

        // 5. Tooltip for hovered chunk
        if (hoveredChunk != null) {
            renderTooltip(w, h, hoveredChunk);
        }
    }

    private void renderGrid(double w, double h) {
        gc.setStroke(Color.web("#161b22"));
        gc.setLineWidth(1.0);

        double cx = (w / 2.0) + panX;
        double cy = (h / 2.0) + panY;

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

    private void renderAxes(double w, double h) {
        double cx = (w / 2.0) + panX;
        double cy = (h / 2.0) + panY;

        // PC1 horizontal axis (y = 0)
        gc.setStroke(Color.web("#30363d"));
        gc.setLineWidth(1.5);
        if (cy >= 0 && cy <= h) {
            gc.strokeLine(0, cy, w, cy);
            gc.setFill(Color.web("#8b949e"));
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            gc.fillText("Principal Component 1 (PC1) →", w - 175, cy - 8);
        }

        // PC2 vertical axis (x = 0)
        if (cx >= 0 && cx <= w) {
            gc.strokeLine(cx, 0, cx, h);
            gc.setFill(Color.web("#8b949e"));
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            gc.fillText("↑ Principal Component 2 (PC2)", cx + 8, 20);
        }
    }

    private void renderPoints(double w, double h) {
        double scaleFactor = Math.min(w, h) * 0.4;

        for (DocumentChunkDto chunk : chunks) {
            // Coordinate transformation from [-1.0, 1.0] to pixel space
            double screenX = (w / 2.0) + (chunk.x2d() * scaleFactor * zoom) + panX;
            double screenY = (h / 2.0) - (chunk.y2d() * scaleFactor * zoom) + panY;

            // Skip far off-screen points
            if (screenX < -50 || screenX > w + 50 || screenY < -50 || screenY > h + 50) {
                continue;
            }

            Color pointColor = categoryColors.getOrDefault(chunk.category(), Color.web("#58a6ff"));
            boolean isHovered = (chunk == hoveredChunk);
            boolean isSelected = (chunk == selectedChunk);

            // Minimum visible radius: 6.0px (12px diameter) to 8.5px
            double r = (isHovered || isSelected) ? 8.5 : 6.0;

            // 1. Semi-transparent glow ring around point
            gc.setFill(new Color(pointColor.getRed(), pointColor.getGreen(), pointColor.getBlue(), 0.35));
            gc.fillOval(screenX - (r + 3.0), screenY - (r + 3.0), (r + 3.0) * 2, (r + 3.0) * 2);

            // 2. Solid opaque fill corresponding to cluster
            gc.setFill(pointColor);
            gc.fillOval(screenX - r, screenY - r, r * 2, r * 2);

            // 3. Crisp high-contrast outline so points pop against dark background
            gc.setStroke(Color.web("#ffffff", 0.85));
            gc.setLineWidth(1.2);
            gc.strokeOval(screenX - r, screenY - r, r * 2, r * 2);

            if (isSelected) {
                gc.setStroke(Color.web("#ffffff", 1.0));
                gc.setLineWidth(2.5);
                gc.strokeOval(screenX - (r + 5.0), screenY - (r + 5.0), (r + 5.0) * 2, (r + 5.0) * 2);
            } else if (isHovered) {
                gc.setStroke(Color.web("#58a6ff", 1.0));
                gc.setLineWidth(2.0);
                gc.strokeOval(screenX - (r + 4.0), screenY - (r + 4.0), (r + 4.0) * 2, (r + 4.0) * 2);
            }
        }
    }

    private void renderTooltip(double w, double h, DocumentChunkDto chunk) {
        double scaleFactor = Math.min(w, h) * 0.4;
        double screenX = (w / 2.0) + (chunk.x2d() * scaleFactor * zoom) + panX;
        double screenY = (h / 2.0) - (chunk.y2d() * scaleFactor * zoom) + panY;

        double boxW = 270;
        double boxH = 84;
        double boxX = screenX + 14;
        double boxY = screenY - 42;

        // Keep inside canvas bounds
        if (boxX + boxW > w - 10) {
            boxX = screenX - boxW - 14;
        }
        if (boxY < 10) {
            boxY = 10;
        }
        if (boxY + boxH > h - 10) {
            boxY = h - boxH - 10;
        }

        // Tooltip box shadow & background
        gc.setFill(Color.web("rgba(22, 27, 34, 0.95)"));
        gc.setStroke(Color.web("#58a6ff"));
        gc.setLineWidth(1.4);
        gc.fillRoundRect(boxX, boxY, boxW, boxH, 8, 8);
        gc.strokeRoundRect(boxX, boxY, boxW, boxH, 8, 8);

        // Header: [ID] Category
        Color catColor = categoryColors.getOrDefault(chunk.category(), Color.web("#58a6ff"));
        gc.setFill(catColor);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        gc.fillText("[" + chunk.chunkId() + "] " + chunk.category(), boxX + 12, boxY + 22);

        // File & Page
        gc.setFill(Color.web("#f0f6fc"));
        gc.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 11));
        String fileText = chunk.fileName() + " (p. " + chunk.pageNumber() + ")";
        if (fileText.length() > 36) {
            fileText = fileText.substring(0, 33) + "...";
        }
        gc.fillText(fileText, boxX + 12, boxY + 40);

        // Snippet excerpt
        gc.setFill(Color.web("#8b949e"));
        gc.setFont(Font.font("Segoe UI", 10));
        String snippet = chunk.textSnippet();
        if (snippet.length() > 48) {
            snippet = snippet.substring(0, 45) + "...";
        }
        gc.fillText(snippet, boxX + 12, boxY + 58);

        gc.setFill(Color.web("#388bfd"));
        gc.fillText("Click to inspect chunk details in inspector", boxX + 12, boxY + 74);
    }
}
