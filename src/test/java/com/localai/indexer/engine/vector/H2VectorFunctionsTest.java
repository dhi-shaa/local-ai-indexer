package com.localai.indexer.engine.vector;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class H2VectorFunctionsTest {

    @Test
    void testCosineSimilarityIdenticalVectors() {
        float[] a = new float[]{1.0f, 2.0f, 3.0f};
        float[] b = new float[]{1.0f, 2.0f, 3.0f};

        double sim = H2VectorFunctions.cosineSimilarity(a, b);
        assertThat(sim).isCloseTo(1.0, within(0.0001));
    }

    @Test
    void testCosineSimilarityOrthogonalVectors() {
        float[] a = new float[]{1.0f, 0.0f, 0.0f};
        float[] b = new float[]{0.0f, 1.0f, 0.0f};

        double sim = H2VectorFunctions.cosineSimilarity(a, b);
        assertThat(sim).isCloseTo(0.0, within(0.0001));
    }

    @Test
    void testCosineSimilarityOppositeVectors() {
        float[] a = new float[]{1.0f, 0.0f};
        float[] b = new float[]{-1.0f, 0.0f};

        double sim = H2VectorFunctions.cosineSimilarity(a, b);
        assertThat(sim).isCloseTo(-1.0, within(0.0001));
    }

    @Test
    void testDotProduct() {
        float[] a = new float[]{2.0f, 3.0f, 4.0f};
        float[] b = new float[]{1.0f, 2.0f, 3.0f};

        double dot = H2VectorFunctions.dotProduct(a, b);
        assertThat(dot).isCloseTo(2.0 * 1.0 + 3.0 * 2.0 + 4.0 * 3.0, within(0.0001));
    }

    @Test
    void testEuclideanDistance() {
        float[] a = new float[]{0.0f, 0.0f};
        float[] b = new float[]{3.0f, 4.0f};

        double dist = H2VectorFunctions.euclideanDistance(a, b);
        assertThat(dist).isCloseTo(5.0, within(0.0001));
    }

    @Test
    void testByteSerializationRoundtrip() {
        float[] original = new float[]{0.123f, -4.56f, 78.9f, 0.0f};
        byte[] bytes = H2VectorFunctions.floatsToBytes(original);
        float[] recovered = H2VectorFunctions.bytesToFloats(bytes);

        assertThat(recovered).containsExactly(original);

        double byteSim = H2VectorFunctions.cosineSimilarityBytes(bytes, bytes);
        assertThat(byteSim).isCloseTo(1.0, within(0.0001));
    }

    @Test
    void testStringParsingAndSimilarity() {
        String strA = "[1.0, 2.0, 3.0]";
        String strB = "[1.0, 2.0, 3.0]";

        double sim = H2VectorFunctions.cosineSimilarityStrings(strA, strB);
        assertThat(sim).isCloseTo(1.0, within(0.0001));
    }
}
