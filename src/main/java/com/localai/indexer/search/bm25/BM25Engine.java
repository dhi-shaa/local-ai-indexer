package com.localai.indexer.search.bm25;

import com.localai.indexer.ingestion.model.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * From-scratch implementation of the Okapi BM25 ranking function.
 * Features an inverted index, stop-word elimination, term frequency caching,
 * and Okapi Robertson-Spärck Jones IDF weighting.
 */
@Component
public class BM25Engine {

    private static final Logger log = LoggerFactory.getLogger(BM25Engine.class);

    // Standard BM25 hyperparameters
    private final double k1;
    private final double b;

    // Inverted index: term -> Map<chunkId, termFrequency>
    private final Map<String, Map<String, Integer>> invertedIndex = new ConcurrentHashMap<>();
    // Document lengths: chunkId -> tokenCount
    private final Map<String, Integer> docLengths = new ConcurrentHashMap<>();
    // Lookup chunk metadata by id
    private final Map<String, DocumentChunk> chunkStore = new ConcurrentHashMap<>();

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren't",
            "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by", "can",
            "can't", "cannot", "could", "couldn't", "did", "didn't", "do", "does", "doesn't", "doing", "don't",
            "down", "during", "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't", "have",
            "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here", "here's", "hers", "herself", "him",
            "himself", "his", "how", "how's", "i", "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is", "isn't",
            "it", "it's", "its", "itself", "let's", "me", "more", "most", "mustn't", "my", "myself", "no", "nor",
            "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves", "out",
            "over", "own", "same", "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't", "so", "some",
            "such", "than", "that", "that's", "the", "their", "theirs", "them", "themselves", "then", "there",
            "there's", "these", "they", "they'd", "they'll", "they're", "they've", "this", "those", "through", "to",
            "too", "under", "until", "up", "very", "was", "wasn't", "we", "we'd", "we'll", "we're", "we've", "were",
            "weren't", "what", "what's", "when", "when's", "where", "where's", "which", "while", "who", "who's",
            "whom", "why", "why's", "with", "won't", "would", "wouldn't", "you", "you'd", "you'll", "you're", "you've",
            "your", "yours", "yourself", "yourselves"
    );

    public BM25Engine() {
        this(1.2, 0.75);
    }

    public BM25Engine(double k1, double b) {
        this.k1 = k1;
        this.b = b;
    }

    /**
     * Index a collection of document chunks.
     */
    public synchronized void indexChunks(Collection<DocumentChunk> chunks) {
        if (chunks == null) return;
        for (DocumentChunk chunk : chunks) {
            indexChunk(chunk);
        }
    }

    /**
     * Index a single document chunk into the inverted index.
     */
    public synchronized void indexChunk(DocumentChunk chunk) {
        if (chunk == null || chunk.getId() == null) return;

        // Remove old entry if updating
        removeChunk(chunk.getId());

        chunkStore.put(chunk.getId(), chunk);
        List<String> tokens = tokenize(chunk.getContent());
        docLengths.put(chunk.getId(), tokens.size());

        Map<String, Integer> termFreqs = new HashMap<>();
        for (String t : tokens) {
            termFreqs.merge(t, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : termFreqs.entrySet()) {
            invertedIndex.computeIfAbsent(entry.getKey(), k -> new ConcurrentHashMap<>())
                    .put(chunk.getId(), entry.getValue());
        }
    }

    public synchronized void removeChunk(String chunkId) {
        chunkStore.remove(chunkId);
        docLengths.remove(chunkId);
        for (Map<String, Integer> postings : invertedIndex.values()) {
            postings.remove(chunkId);
        }
    }

    public synchronized void clear() {
        invertedIndex.clear();
        docLengths.clear();
        chunkStore.clear();
    }

    /**
     * Query the inverted index using Okapi BM25 formula.
     */
    public List<BM25ScoredResult> search(String query, int topK) {
        if (query == null || query.isBlank() || docLengths.isEmpty() || topK <= 0) {
            return Collections.emptyList();
        }

        List<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return Collections.emptyList();
        }

        int totalDocs = docLengths.size();
        double avgDl = computeAverageDocumentLength();

        Map<String, Double> chunkScores = new HashMap<>();

        for (String term : queryTerms) {
            Map<String, Integer> postings = invertedIndex.get(term);
            if (postings == null || postings.isEmpty()) {
                continue;
            }

            int docFreq = postings.size();
            // Okapi Robertson-Spärck Jones IDF
            double idf = Math.log(1.0 + (totalDocs - docFreq + 0.5) / (docFreq + 0.5));
            if (idf <= 0.0) {
                idf = 0.001;
            }

            for (Map.Entry<String, Integer> entry : postings.entrySet()) {
                String chunkId = entry.getKey();
                int tf = entry.getValue();
                int docLen = docLengths.getOrDefault(chunkId, (int) avgDl);

                double numerator = tf * (k1 + 1.0);
                double denominator = tf + k1 * (1.0 - b + b * (docLen / avgDl));
                double termScore = idf * (numerator / denominator);

                chunkScores.merge(chunkId, termScore, Double::sum);
            }
        }

        List<BM25ScoredResult> results = new ArrayList<>(chunkScores.size());
        for (Map.Entry<String, Double> entry : chunkScores.entrySet()) {
            DocumentChunk chunk = chunkStore.get(entry.getKey());
            if (chunk != null) {
                results.add(new BM25ScoredResult(chunk, entry.getValue()));
            }
        }

        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        if (results.size() > topK) {
            return results.subList(0, topK);
        }
        return results;
    }

    private double computeAverageDocumentLength() {
        if (docLengths.isEmpty()) return 1.0;
        long totalTokens = 0;
        for (int len : docLengths.values()) {
            totalTokens += len;
        }
        return (double) totalTokens / docLengths.size();
    }

    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        String clean = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ");
        String[] rawTokens = clean.split("\\s+");
        List<String> tokens = new ArrayList<>(rawTokens.length);

        for (String t : rawTokens) {
            String term = t.trim();
            if (term.length() > 1 && !STOP_WORDS.contains(term)) {
                tokens.add(term);
            }
        }
        return tokens;
    }

    public int getIndexedChunkCount() {
        return docLengths.size();
    }

    public static class BM25ScoredResult {
        private final DocumentChunk chunk;
        private final double score;

        public BM25ScoredResult(DocumentChunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }

        public DocumentChunk getChunk() {
            return chunk;
        }

        public double getScore() {
            return score;
        }
    }
}
