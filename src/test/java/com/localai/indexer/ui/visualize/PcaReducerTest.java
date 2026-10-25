package com.localai.indexer.ui.visualize;

import com.localai.indexer.model.DocumentChunkDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PcaReducerTest {

    @Test
    public void testPcaDimensionalityReduction() {
        // Create 20 synthetic chunks with 8 dimensions
        List<DocumentChunkDto> chunks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            double[] emb = new double[8];
            for (int d = 0; d < 8; d++) {
                emb[d] = (i % 2 == 0) ? (d * 1.5 + i) : -(d * 0.8 + i);
            }
            chunks.add(new DocumentChunkDto(
                "chk_" + i,
                "file_" + i + ".md",
                1,
                "CategoryA",
                "Snippet text " + i,
                emb,
                0.0,
                0.0
            ));
        }

        PcaReducer.PcaResult result = PcaReducer.reduceTo2D(chunks);

        assertNotNull(result);
        assertEquals(20, result.projectedChunks().size());
        assertTrue(result.explainedVarianceRatio1() > 0.0, "Explained variance 1 should be positive");
        assertTrue(result.explainedVarianceRatio2() >= 0.0, "Explained variance 2 should be non-negative");

        // Verify bounds are within [-1.0, 1.0]
        for (DocumentChunkDto projected : result.projectedChunks()) {
            assertTrue(projected.x2d() >= -1.0 - 1e-6 && projected.x2d() <= 1.0 + 1e-6,
                "x coordinate should be in [-1.0, 1.0] but was " + projected.x2d());
            assertTrue(projected.y2d() >= -1.0 - 1e-6 && projected.y2d() <= 1.0 + 1e-6,
                "y coordinate should be in [-1.0, 1.0] but was " + projected.y2d());
        }
    }

    @Test
    public void testPcaSingleChunkHandling() {
        List<DocumentChunkDto> single = List.of(new DocumentChunkDto(
            "chk_single", "doc.txt", 1, "General", "Text", new double[]{1.0, 2.0, 3.0}, 0.0, 0.0
        ));

        PcaReducer.PcaResult result = PcaReducer.reduceTo2D(single);
        assertNotNull(result);
        assertEquals(1, result.projectedChunks().size());
        assertEquals(0.0, result.projectedChunks().get(0).x2d());
        assertEquals(0.0, result.projectedChunks().get(0).y2d());
    }

    @Test
    public void testPcaEmptyListHandling() {
        PcaReducer.PcaResult result = PcaReducer.reduceTo2D(List.of());
        assertNotNull(result);
        assertTrue(result.projectedChunks().isEmpty());
    }
}
