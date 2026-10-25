package com.localai.indexer.engine.vector;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Native H2 User-Defined Functions (UDF) for vector operations.
 * Registered as H2 SQL ALIASes to provide vector extensions without external dependencies like PGVector.
 */
public class H2VectorFunctions {

    /**
     * Compute cosine similarity between two float vectors represented as byte arrays.
     */
    public static double cosineSimilarityBytes(byte[] bytesA, byte[] bytesB) {
        if (bytesA == null || bytesB == null || bytesA.length == 0 || bytesB.length == 0) {
            return 0.0;
        }
        float[] vecA = bytesToFloats(bytesA);
        float[] vecB = bytesToFloats(bytesB);
        return cosineSimilarity(vecA, vecB);
    }

    /**
     * Compute cosine similarity between two vectors represented as comma-delimited strings.
     */
    public static double cosineSimilarityStrings(String strA, String strB) {
        if (strA == null || strB == null || strA.isBlank() || strB.isBlank()) {
            return 0.0;
        }
        float[] vecA = parseVectorString(strA);
        float[] vecB = parseVectorString(strB);
        return cosineSimilarity(vecA, vecB);
    }

    /**
     * Core cosine similarity computation between two float arrays.
     */
    public static double cosineSimilarity(float[] vecA, float[] vecB) {
        if (vecA == null || vecB == null) {
            return 0.0;
        }
        int minLen = Math.min(vecA.length, vecB.length);
        if (minLen == 0) {
            return 0.0;
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < minLen; i++) {
            float a = vecA[i];
            float b = vecB[i];
            dot += a * b;
            normA += a * a;
            normB += b * b;
        }

        if (normA <= 0.0 || normB <= 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Compute dot product between two float vectors.
     */
    public static double dotProduct(float[] vecA, float[] vecB) {
        if (vecA == null || vecB == null) {
            return 0.0;
        }
        int minLen = Math.min(vecA.length, vecB.length);
        double dot = 0.0;
        for (int i = 0; i < minLen; i++) {
            dot += vecA[i] * vecB[i];
        }
        return dot;
    }

    public static double dotProductStrings(String strA, String strB) {
        if (strA == null || strB == null || strA.isBlank() || strB.isBlank()) {
            return 0.0;
        }
        float[] vecA = parseVectorString(strA);
        float[] vecB = parseVectorString(strB);
        return dotProduct(vecA, vecB);
    }

    /**
     * Compute Euclidean distance between two float vectors.
     */
    public static double euclideanDistance(float[] vecA, float[] vecB) {
        if (vecA == null || vecB == null) {
            return Double.MAX_VALUE;
        }
        int minLen = Math.min(vecA.length, vecB.length);
        double sumSq = 0.0;
        for (int i = 0; i < minLen; i++) {
            double diff = vecA[i] - vecB[i];
            sumSq += diff * diff;
        }
        return Math.sqrt(sumSq);
    }

    public static double euclideanDistanceStrings(String strA, String strB) {
        if (strA == null || strB == null || strA.isBlank() || strB.isBlank()) {
            return Double.MAX_VALUE;
        }
        float[] vecA = parseVectorString(strA);
        float[] vecB = parseVectorString(strB);
        return euclideanDistance(vecA, vecB);
    }

    /**
     * Convert float array to compact IEEE 754 32-bit big-endian byte array.
     */
    public static byte[] floatsToBytes(float[] floats) {
        if (floats == null) {
            return new byte[0];
        }
        ByteBuffer buffer = ByteBuffer.allocate(floats.length * 4).order(ByteOrder.BIG_ENDIAN);
        for (float f : floats) {
            buffer.putFloat(f);
        }
        return buffer.array();
    }

    /**
     * Convert IEEE 754 byte array back to float array.
     */
    public static float[] bytesToFloats(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return new float[0];
        }
        int count = bytes.length / 4;
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        float[] floats = new float[count];
        for (int i = 0; i < count; i++) {
            floats[i] = buffer.getFloat();
        }
        return floats;
    }

    /**
     * Helper to parse comma or JSON array formatted strings into float arrays.
     */
    public static float[] parseVectorString(String input) {
        if (input == null || input.isBlank()) {
            return new float[0];
        }
        String clean = input.trim();
        if (clean.startsWith("[") && clean.endsWith("]")) {
            clean = clean.substring(1, clean.length() - 1);
        }
        String[] tokens = clean.split("[,\\s]+");
        float[] result = new float[tokens.length];
        int idx = 0;
        for (String t : tokens) {
            if (!t.isBlank()) {
                try {
                    result[idx++] = Float.parseFloat(t.trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return Arrays.copyOf(result, idx);
    }

    public static String floatsToString(float[] floats) {
        if (floats == null || floats.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < floats.length; i++) {
            sb.append(floats[i]);
            if (i < floats.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
