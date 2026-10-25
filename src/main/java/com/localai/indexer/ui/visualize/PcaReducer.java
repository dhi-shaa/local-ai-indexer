package com.localai.indexer.ui.visualize;

import com.localai.indexer.model.DocumentChunkDto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Pure Java Principal Component Analysis (PCA) implementation.
 * Reduces high-dimensional vector embeddings to 2D coordinate projections
 * for interactive visual rendering.
 *
 * Employs matrix-free power iteration with Gram-Schmidt orthogonal deflation
 * for optimal O(N * D) performance across arbitrary vector dimensions.
 */
public class PcaReducer {

    public record PcaResult(
        List<DocumentChunkDto> projectedChunks,
        double explainedVarianceRatio1,
        double explainedVarianceRatio2,
        long computeTimeMs
    ) {}

    /**
     * Reduces high-dimensional embeddings of document chunks to 2D coordinates.
     *
     * @param chunks List of chunks with raw embedding vectors
     * @return PcaResult containing chunks updated with normalized (x2d, y2d) in [-1.0, 1.0] range
     */
    public static PcaResult reduceTo2D(List<DocumentChunkDto> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new PcaResult(List.of(), 0.0, 0.0, 0);
        }

        long startTime = System.currentTimeMillis();
        int n = chunks.size();
        int d = chunks.get(0).embedding().length;

        // If only 1 chunk or 0 dimensions
        if (n == 1 || d == 0) {
            List<DocumentChunkDto> single = List.of(chunks.get(0).withCoordinates(0.0, 0.0));
            return new PcaResult(single, 1.0, 0.0, 0);
        }

        // 1. Extract data matrix and compute column means
        double[][] data = new double[n][d];
        double[] mean = new double[d];
        for (int i = 0; i < n; i++) {
            double[] emb = chunks.get(i).embedding();
            for (int j = 0; j < d; j++) {
                data[i][j] = (j < emb.length) ? emb[j] : 0.0;
                mean[j] += data[i][j];
            }
        }
        for (int j = 0; j < d; j++) {
            mean[j] /= n;
        }

        // 2. Mean-center the data: Z = X - mean
        double[][] z = new double[n][d];
        double totalVariance = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < d; j++) {
                z[i][j] = data[i][j] - mean[j];
                totalVariance += z[i][j] * z[i][j];
            }
        }
        if (totalVariance == 0.0) {
            totalVariance = 1.0;
        }

        // 3. Compute 1st Principal Component (v1) via Power Iteration
        double[] v1 = powerIteration(z, null, 60);

        // 4. Compute 2nd Principal Component (v2) via Deflated Power Iteration
        double[] v2 = powerIteration(z, v1, 60);

        // 5. Project data points onto v1 and v2
        double[] projX = new double[n];
        double[] projY = new double[n];
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double var1 = 0.0, var2 = 0.0;

        for (int i = 0; i < n; i++) {
            double dot1 = 0.0;
            double dot2 = 0.0;
            for (int j = 0; j < d; j++) {
                dot1 += z[i][j] * v1[j];
                dot2 += z[i][j] * v2[j];
            }
            projX[i] = dot1;
            projY[i] = dot2;
            var1 += dot1 * dot1;
            var2 += dot2 * dot2;

            if (dot1 < minX) minX = dot1;
            if (dot1 > maxX) maxX = dot1;
            if (dot2 < minY) minY = dot2;
            if (dot2 > maxY) maxY = dot2;
        }

        // 6. Normalize coordinates into [-1.0, 1.0] viewport bounds
        double rangeX = (maxX - minX > 1e-6) ? (maxX - minX) : 1.0;
        double rangeY = (maxY - minY > 1e-6) ? (maxY - minY) : 1.0;

        List<DocumentChunkDto> projectedList = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double normX = -1.0 + 2.0 * ((projX[i] - minX) / rangeX);
            double normY = -1.0 + 2.0 * ((projY[i] - minY) / rangeY);
            projectedList.add(chunks.get(i).withCoordinates(normX, normY));
        }

        long elapsed = System.currentTimeMillis() - startTime;
        double ratio1 = Math.min(1.0, var1 / totalVariance);
        double ratio2 = Math.min(1.0, var2 / totalVariance);

        return new PcaResult(projectedList, ratio1, ratio2, elapsed);
    }

    /**
     * Performs matrix-free power iteration on covariance C = Z^T * Z / (N - 1).
     * If orthogonalTo is provided, deflates the vector at every step to ensure orthogonality.
     */
    private static double[] powerIteration(double[][] z, double[] orthogonalTo, int maxIterations) {
        int n = z.length;
        int d = z[0].length;
        Random rng = new Random(orthogonalTo == null ? 101 : 202);

        double[] v = new double[d];
        for (int j = 0; j < d; j++) {
            v[j] = rng.nextGaussian();
        }

        if (orthogonalTo != null) {
            projectOut(v, orthogonalTo);
        }
        normalize(v);

        double[] u = new double[n]; // buffer for Z * v
        double[] w = new double[d]; // buffer for Z^T * u

        for (int iter = 0; iter < maxIterations; iter++) {
            // u = Z * v
            for (int i = 0; i < n; i++) {
                double sum = 0.0;
                for (int j = 0; j < d; j++) {
                    sum += z[i][j] * v[j];
                }
                u[i] = sum;
            }

            // w = Z^T * u
            Arrays.fill(w, 0.0);
            for (int i = 0; i < n; i++) {
                double ui = u[i];
                for (int j = 0; j < d; j++) {
                    w[j] += z[i][j] * ui;
                }
            }

            // Deflation / Orthogonalization against previous eigenvector
            if (orthogonalTo != null) {
                projectOut(w, orthogonalTo);
            }

            double norm = norm(w);
            if (norm < 1e-12) {
                break;
            }
            for (int j = 0; j < d; j++) {
                v[j] = w[j] / norm;
            }
        }

        return v;
    }

    private static void projectOut(double[] v, double[] u) {
        double dot = 0.0;
        for (int i = 0; i < v.length; i++) {
            dot += v[i] * u[i];
        }
        for (int i = 0; i < v.length; i++) {
            v[i] -= dot * u[i];
        }
    }

    private static double norm(double[] v) {
        double sum = 0.0;
        for (double val : v) {
            sum += val * val;
        }
        return Math.sqrt(sum);
    }

    private static void normalize(double[] v) {
        double n = norm(v);
        if (n > 1e-12) {
            for (int i = 0; i < v.length; i++) {
                v[i] /= n;
            }
        }
    }
}
