# Technical Report: Low-Latency Semantic Embedding Microservices
Team: Machine Learning Platform
Release: v3.4-prod

## 1. Transformer Quantization & Inference
We deploy ONNX Runtime with INT8 quantization for dense embedding models derived from BAAI/bge-small-en-v1.5.
By mapping model weights to INT8, memory footprints drop from 134MB to 33MB with less than 0.8% loss in Mean Reciprocal Rank (MRR).

## 2. Pooling Strategies
Token-level representations are consolidated via Mean Pooling across non-padding tokens.
Final representations are L2-normalized:
v_norm = v / max(||v||_2, 1e-12)
This ensures inner products correlate strictly to cosine similarity in $[-1.0, 1.0]$.

## 3. Dimensionality Reduction Benchmarks
When testing high-dimensional embeddings (384-d), PCA power iteration achieves an explained variance ratio of 48.7% 
across the first two principal components within 4.2ms on commodity AVX2 CPU cores.
