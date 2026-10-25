package com.localai.indexer.ui.chat;

import com.localai.indexer.model.HybridSearchResultDto;
import com.localai.indexer.service.MockPipelineBackendService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Main chat interface for querying indexed local repositories.
 * Connected to hybrid search and synchronizes with the collapsible SourceCitationPanel.
 */
public class ChatInterfaceView extends BorderPane {

    private final VBox messageHistoryContainer;
    private final ScrollPane scrollPane;
    private final TextField queryInput = new TextField();
    private final Button sendButton = new Button("Search & Retrieve ⏎");
    private final SourceCitationPanel citationPanel;

    private final List<HybridSearchResultDto> queryHistory = new ArrayList<>();

    public ChatInterfaceView() {
        setStyle("-fx-background-color: #0d1117;");

        // Citations side panel
        citationPanel = new SourceCitationPanel();
        setRight(citationPanel);

        // Center chat area
        VBox chatArea = new VBox();
        chatArea.setPadding(new Insets(16));
        setCenter(chatArea);

        // Top quick suggestions
        HBox suggestions = createSuggestionsBar();

        // Message history scroll pane
        messageHistoryContainer = new VBox(14);
        messageHistoryContainer.setPadding(new Insets(8));

        scrollPane = new ScrollPane(messageHistoryContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        // Input bar
        HBox inputBar = createInputBar();

        chatArea.getChildren().addAll(suggestions, scrollPane, inputBar);

        // Welcome initial message
        addWelcomeMessage();
    }

    private HBox createSuggestionsBar() {
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 10, 0));

        Label hint = new Label("Quick Inquiries:");
        hint.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 11px;");

        Button q1 = createChip("🔒 Local Air-Gap Privacy", "Explain the zero-outbound network air-gap privacy guarantee");
        Button q2 = createChip("🧠 PCA 2D Embedding Map", "How does the PCA 2D embedding dimensionality reduction work?");
        Button q3 = createChip("⚡ Hybrid Search Retrieval", "Explain hybrid search with dense vectors and BM25 keywords");

        bar.getChildren().addAll(hint, q1, q2, q3);
        return bar;
    }

    private Button createChip(String label, String queryText) {
        Button chip = new Button(label);
        chip.setStyle("-fx-background-color: #21262d; -fx-text-fill: #c9d1d9; -fx-font-size: 11px; -fx-background-radius: 12px; -fx-border-color: #30363d; -fx-border-radius: 12px; -fx-padding: 3 10 3 10; -fx-cursor: hand;");
        chip.setOnMouseEntered(e -> chip.setStyle("-fx-background-color: #30363d; -fx-text-fill: #ffffff; -fx-font-size: 11px; -fx-background-radius: 12px; -fx-border-color: #58a6ff; -fx-border-radius: 12px; -fx-padding: 3 10 3 10; -fx-cursor: hand;"));
        chip.setOnMouseExited(e -> chip.setStyle("-fx-background-color: #21262d; -fx-text-fill: #c9d1d9; -fx-font-size: 11px; -fx-background-radius: 12px; -fx-border-color: #30363d; -fx-border-radius: 12px; -fx-padding: 3 10 3 10; -fx-cursor: hand;"));
        chip.setOnAction(e -> {
            queryInput.setText(queryText);
            handleSendQuery();
        });
        return chip;
    }

    private HBox createInputBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER);
        bar.setPadding(new Insets(10, 0, 0, 0));

        queryInput.setPromptText("Ask anything across indexed technical documents and repositories...");
        queryInput.setStyle("-fx-background-color: #161b22; -fx-text-fill: #e6edf3; -fx-prompt-text-fill: #8b949e; -fx-border-color: #30363d; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 10 14 10 14; -fx-font-size: 13px;");
        HBox.setHgrow(queryInput, Priority.ALWAYS);

        queryInput.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                handleSendQuery();
            }
        });

        sendButton.setStyle("-fx-background-color: #238636; -fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 10 16 10 16; -fx-background-radius: 6px; -fx-cursor: hand;");
        sendButton.setOnAction(e -> handleSendQuery());

        bar.getChildren().addAll(queryInput, sendButton);
        return bar;
    }

    private void addWelcomeMessage() {
        VBox bubble = new VBox(6);
        bubble.setPadding(new Insets(12, 16, 12, 16));
        bubble.setStyle("-fx-background-color: #161b22; -fx-background-radius: 8px; -fx-border-color: #30363d; -fx-border-radius: 8px;");
        bubble.setMaxWidth(700);

        Label header = new Label("Local AI Indexer Assistant • 100% Offline");
        header.setStyle("-fx-text-fill: #3fb950; -fx-font-weight: bold; -fx-font-size: 12px;");

        Label body = new Label("Repository search ready. Documents are ingested locally with Apache Tika, chunked, and embedded into local H2 vector tables. Zero internet connections are made. Ask a question to view hybrid retrieval with exact source citations.");
        body.setWrapText(true);
        body.setStyle("-fx-text-fill: #e6edf3; -fx-font-size: 12px;");

        bubble.getChildren().addAll(header, body);
        messageHistoryContainer.getChildren().add(bubble);
    }

    private void handleSendQuery() {
        String query = queryInput.getText().trim();
        if (query.isEmpty()) return;

        queryInput.clear();

        // 1. Render User Message Bubble
        addUserBubble(query);

        // 2. Execute Hybrid Search
        HybridSearchResultDto result = MockPipelineBackendService.getInstance().executeSearch(query);
        queryHistory.add(result);

        // 3. Render Assistant Message Bubble
        addAssistantBubble(result);

        // 4. Auto-populate side-panel with citations
        citationPanel.displayCitations(result.citations());

        // Scroll to bottom
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }

    private void addUserBubble(String query) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_RIGHT);

        VBox bubble = new VBox(4);
        bubble.setPadding(new Insets(10, 14, 10, 14));
        bubble.setStyle("-fx-background-color: #1f6feb; -fx-background-radius: 8px 8px 2px 8px;");
        bubble.setMaxWidth(600);

        Label text = new Label(query);
        text.setWrapText(true);
        text.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 12px;");

        bubble.getChildren().add(text);
        row.getChildren().add(bubble);
        messageHistoryContainer.getChildren().add(row);
    }

    private void addAssistantBubble(HybridSearchResultDto result) {
        VBox bubble = new VBox(8);
        bubble.setPadding(new Insets(12, 16, 12, 16));
        bubble.setStyle("-fx-background-color: #161b22; -fx-background-radius: 8px 8px 8px 2px; -fx-border-color: #30363d; -fx-border-radius: 8px; -fx-cursor: hand;");
        bubble.setMaxWidth(720);

        // Top meta row
        HBox metaRow = new HBox(8);
        metaRow.setAlignment(Pos.CENTER_LEFT);

        Label header = new Label("Local AI Response");
        header.setStyle("-fx-text-fill: #58a6ff; -fx-font-weight: bold; -fx-font-size: 12px;");

        Label latencyBadge = new Label(result.latencyMs() + " ms");
        latencyBadge.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 10px;");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button citationBadge = new Button("📄 " + result.citations().size() + " Source Citations");
        citationBadge.setStyle("-fx-background-color: rgba(56, 139, 253, 0.15); -fx-text-fill: #58a6ff; -fx-font-size: 11px; -fx-padding: 3 8 3 8; -fx-background-radius: 4px; -fx-border-color: #388bfd; -fx-border-radius: 4px; -fx-cursor: hand;");
        citationBadge.setOnAction(e -> citationPanel.displayCitations(result.citations()));

        metaRow.getChildren().addAll(header, latencyBadge, spacer, citationBadge);

        // Answer text
        Label answerText = new Label(result.answer());
        answerText.setWrapText(true);
        answerText.setStyle("-fx-text-fill: #e6edf3; -fx-font-size: 12px; -fx-line-spacing: 2px;");

        // Click to view citations hint
        Label clickHint = new Label("Click this response to view citations in the right-hand panel");
        clickHint.setStyle("-fx-text-fill: #8b949e; -fx-font-size: 10px; -fx-font-style: italic;");

        bubble.getChildren().addAll(metaRow, answerText, clickHint);

        // Clicking the response bubble selects it and populates citations panel
        bubble.setOnMouseClicked(e -> {
            citationPanel.displayCitations(result.citations());
            bubble.setStyle("-fx-background-color: #1c2128; -fx-background-radius: 8px 8px 8px 2px; -fx-border-color: #58a6ff; -fx-border-radius: 8px; -fx-cursor: hand;");
        });
        bubble.setOnMouseEntered(e -> {
            bubble.setStyle("-fx-background-color: #1c2128; -fx-background-radius: 8px 8px 8px 2px; -fx-border-color: #388bfd; -fx-border-radius: 8px; -fx-cursor: hand;");
        });
        bubble.setOnMouseExited(e -> {
            bubble.setStyle("-fx-background-color: #161b22; -fx-background-radius: 8px 8px 8px 2px; -fx-border-color: #30363d; -fx-border-radius: 8px; -fx-cursor: hand;");
        });

        messageHistoryContainer.getChildren().add(bubble);
    }
}
