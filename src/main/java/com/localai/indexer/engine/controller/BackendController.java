package com.localai.indexer.engine.controller;

import com.localai.indexer.engine.dto.BackendStatusDto;
import com.localai.indexer.engine.dto.IndexRequestDto;
import com.localai.indexer.engine.dto.IndexResponseDto;
import com.localai.indexer.engine.service.BackendService;
import com.localai.indexer.search.dto.SearchRequestDto;
import com.localai.indexer.search.dto.SearchResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Local REST Controller for JavaFX frontend polling and communication.
 * Enables loose coupling between frontend UI process and backend indexing engine.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class BackendController {

    private final BackendService backendService;

    public BackendController(BackendService backendService) {
        this.backendService = backendService;
    }

    @GetMapping("/status")
    public ResponseEntity<BackendStatusDto> getStatus() {
        return ResponseEntity.ok(backendService.getStatus());
    }

    @PostMapping({"/index", "/ingest"})
    public ResponseEntity<IndexResponseDto> indexDocument(@RequestBody IndexRequestDto request) {
        IndexResponseDto response = backendService.indexDocument(request);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/search")
    public ResponseEntity<SearchResponseDto> search(@RequestBody SearchRequestDto request) {
        return ResponseEntity.ok(backendService.search(request));
    }

    @GetMapping("/documents")
    public ResponseEntity<List<Map<String, Object>>> listDocuments() {
        return ResponseEntity.ok(backendService.listDocuments());
    }

    @GetMapping("/documents/{id}/chunks")
    public ResponseEntity<List<com.localai.indexer.ingestion.model.DocumentChunk>> getDocumentChunks(@PathVariable("id") String id) {
        return ResponseEntity.ok(backendService.getDocumentChunks(id));
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable("id") String id) {
        boolean deleted = backendService.deleteDocument(id);
        return ResponseEntity.ok(Map.of("success", deleted, "documentId", id));
    }

    @PostMapping("/clear")
    public ResponseEntity<Map<String, Object>> clearDatabase() {
        backendService.clearAll();
        return ResponseEntity.ok(Map.of("success", true, "message", "Database cleared"));
    }
}
