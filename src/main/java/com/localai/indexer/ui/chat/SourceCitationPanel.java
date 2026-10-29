package com.localai.indexer.ui.chat;

import com.localai.indexer.model.SourceCitation;
import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;

/**
 * Collapsible side-panel for displaying source citations returned by hybrid search.
 * Shows the exact chunk ID, source file name, page number, relevance score, and snippet text.
 */
public class SourceCitationPanel extends VBox {

    private final VBox citationsContainer;
    private final Label citationCountBadge;
    private final Label statusMessage;
    private final Button collapseToggleBtn;
    private boolean isCollapsed = false;

    private static final double EXPANDED_WIDTH = 340.0;
    private static final double COLLAPSED_WIDTH = 48.0;

    public SourceCitationPanel() {
        setPrefWidth(EXPANDED_WIDTH);
        setMinWidth(EXPANDED_WIDTH);
        setMaxWidth(EXPANDED_WIDTH);
        setStyle("-fx-background-color: #161b22; -fx-border-color: #30363d; -fx-border-width: 0 0 0 1;");
        setPadding(new Insets(14));

        // Header
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("Source Citations");
        title.setStyle("-fx-text-fill: #e6edf3; -fx-font-size: 14px; -fx-font-weight: bold;");

        citationCountBadge = new Label("0");
        citationCountBadge.setStyle("-fx-background-color: rgba(56, 139, 253, 0.2); -fx-text-fill: #58a6ff; -fx-padding: 2 6 2 6; -fx-background-radius: 10px; -fx-font-size: 10px; -fx-font-weight: bold;");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        collapseToggleBtn = new Button("▶");
        collapseToggleBtn.setTooltip(new javafx.scene.control.Tooltip("Collapse/Expand Citations Panel"));
        collapseToggleBtn.setStyle("-fx-background-color: #21262d; -fx-text-fill: #8b949e; -fx-font-size: 11px; -fx-cursor: hand; -fx-background-radius: 4px;");
        collapseToggleBtn.setOnAction(e -> toggleCollapse());

        header.getChildren().addAll(title, citationCountBadge, spacer, collapseToggleBtn);

        // Subtitle
        Label subtitle = new Label("Exact provenance metadata from hybrid search");
        subtitle.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px;");

        // Citations scroll container
        citationsContainer = new VBox(12);
        citationsContainer.setPadding(new Insets(8, 0, 8, 0));

        statusMessage = new Label("Click on any assistant message to inspect its source citations, page numbers, and exact chunk snippets.");
        statusMessage.setWrapText(true);
        statusMessage.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px; -fx-padding: 12; -fx-background-color: #0d1117; -fx-background-radius: 6px; -fx-border-color: #21262d; -fx-border-radius: 6px;");

        citationsContainer.getChildren().add(statusMessage);

        ScrollPane scrollPane = new ScrollPane(citationsContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().addAll(header, subtitle, new Separator(), scrollPane);
    }

    public void toggleCollapse() {
        isCollapsed = !isCollapsed;
        if (isCollapsed) {
            setPrefWidth(COLLAPSED_WIDTH);
            setMinWidth(COLLAPSED_WIDTH);
            setMaxWidth(COLLAPSED_WIDTH);
            collapseToggleBtn.setText("◀");
            // Hide details when collapsed
            citationsContainer.setVisible(false);
        } else {
            setPrefWidth(EXPANDED_WIDTH);
            setMinWidth(EXPANDED_WIDTH);
            setMaxWidth(EXPANDED_WIDTH);
            collapseToggleBtn.setText("▶");
            citationsContainer.setVisible(true);
        }
    }

    /**
     * Populates the panel with citations from the backend's hybrid search DTO.
     */
    public void displayCitations(List<SourceCitation> citations) {
        if (isCollapsed) {
            toggleCollapse(); // Auto-expand when citations arrive
        }

        citationsContainer.getChildren().clear();

        if (citations == null || citations.isEmpty()) {
            citationCountBadge.setText("0");
            citationsContainer.getChildren().add(statusMessage);
            return;
        }

        citationCountBadge.setText(String.valueOf(citations.size()));

        for (int i = 0; i < citations.size(); i++) {
            SourceCitation citation = citations.get(i);
            VBox card = createCitationCard(citation, i + 1);
            citationsContainer.getChildren().add(card);
        }

        FadeTransition ft = new FadeTransition(Duration.millis(250), citationsContainer);
        ft.setFromValue(0.4);
        ft.setToValue(1.0);
        ft.play();
    }

    private VBox createCitationCard(SourceCitation citation, int index) {
        VBox card = new VBox(6);
        card.setPadding(new Insets(10));
        card.setStyle("-fx-background-color: #0d1117; -fx-background-radius: 6px; -fx-border-color: #30363d; -fx-border-radius: 6px;");

        // Top line: [#index] Chunk Index and Match Score
        HBox topRow = new HBox(6);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label indexPill = new Label("[" + index + "] Chunk #" + citation.getDisplayChunkIndex());
        indexPill.setStyle("-fx-text-fill: #58a6ff; -fx-font-weight: bold; -fx-font-size: 11px;");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label scorePill = new Label("Score: " + citation.getFormattedScore());
        scorePill.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        scorePill.setStyle("-fx-background-color: rgba(35, 134, 54, 0.2); -fx-text-fill: #3fb950; -fx-font-size: 10px; -fx-padding: 1 5 1 5; -fx-background-radius: 4px; -fx-font-weight: bold;");

        topRow.getChildren().addAll(indexPill, spacer, scorePill);

        // File and page
        Label fileLabel = new Label("📄 " + citation.fileName());
        fileLabel.setWrapText(true);
        fileLabel.setStyle("-fx-text-fill: #e6edf3; -fx-font-size: 11px; -fx-font-weight: bold;");

        Label pageLabel = new Label("📍 " + citation.getPageDisplay() + " | " + citation.sectionHeader());
        pageLabel.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 10px;");

        // Snippet excerpt
        Label snippetBox = new Label("\"" + citation.textSnippet() + "\"");
        snippetBox.setWrapText(true);
        snippetBox.setStyle("-fx-text-fill: #c9d1d9; -fx-font-size: 11px; -fx-background-color: #161b22; -fx-padding: 8; -fx-background-radius: 4px; -fx-border-color: #21262d; -fx-border-radius: 4px;");

        // Copy button
        HBox bottomRow = new HBox();
        bottomRow.setAlignment(Pos.CENTER_RIGHT);

        Button copyBtn = new Button("Copy Snippet");
        copyBtn.setStyle("-fx-background-color: #21262d; -fx-text-fill: #8b949e; -fx-font-size: 10px; -fx-cursor: hand; -fx-background-radius: 4px;");
        copyBtn.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(citation.textSnippet());
            clipboard.setContent(content);
            copyBtn.setText("Copied!");
            copyBtn.setStyle("-fx-background-color: #238636; -fx-text-fill: #ffffff; -fx-font-size: 10px; -fx-background-radius: 4px;");
        });

        bottomRow.getChildren().add(copyBtn);

        card.getChildren().addAll(topRow, fileLabel, pageLabel, snippetBox, bottomRow);
        return card;
    }
}
