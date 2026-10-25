# Local AI Privacy Indexer & Vector Search (Air-Gapped Desktop RAG)

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-green.svg)](https://spring.io/projects/spring-boot)
[![JavaFX](https://img.shields.io/badge/JavaFX-21-blue.svg)](https://openjfx.io/)
[![H2 Vector](https://img.shields.io/badge/Storage-H2%20Vector-lightgrey.svg)](https://www.h2database.com/)
[![Ollama](https://img.shields.io/badge/Ollama-Offline%20LLM-black.svg)](https://ollama.com/)
[![Air-Gap Verified](https://img.shields.io/badge/Air--Gap-100%25%20Verified-brightgreen.svg)]()

A high-performance, strictly offline desktop document search and synthesis platform built with **Java 21**, **Spring Boot**, and **JavaFX**. The application executes an end-to-end Retrieval-Augmented Generation (RAG) pipeline locally on the client machine—delivering semantic search, live 2D vector space visualizations, and grounded LLM synthesis with zero outbound network telemetry.

---

## Key Highlights

- **100% Air-Gapped Local Operation**: Operates strictly on loopback (`127.0.0.1`). Features an integrated socket monitor verifying 0 outbound packets, 0 remote connections, and zero external telemetry.
- **Hybrid Retrieval Architecture**: Combines exact lexical term matching via an in-memory **BM25 Inverted Index** with semantic concept matching via **H2 In-Memory Vector Search** (Cosine Similarity).
- **Interactive 2D Embedding Space Visualizer**: Real-time Principal Component Analysis (PCA) projection reducing high-dimensional embeddings to 2D screen coordinates with dynamic panning, zooming, and interactive chunk provenance inspection.
- **Local LLM Synthesis via Ollama**: Direct loopback integration with local models (`phi3`, `llama3.2`) to synthesize direct answers from retrieved chunks with zero hallucinations and precise file/page source citations.
- **Real-Time Telemetry Pipeline**: Live monitoring of document parsing (Apache Tika), semantic chunking, ONNX embedding generation, and vector index persistence latencies.

---

## Architecture Overview

```
                      +-------------------------------------------------------------+
                      |                    JavaFX Desktop UI                        |
                      |  [Search & Citations]  [Pipeline Telemetry]  [2D PCA Map]   |
                      +------------------------------+------------------------------+
                                                     | Loopback HTTP / IPC
                                                     v
+-----------------------------------------------------------------------------------+
| Spring Boot Application Backend (127.0.0.1)                                       |
|                                                                                   |
|  +--------------------+     +---------------------+     +----------------------+  |
|  | Document Ingestion | --> | Semantic Chunking   | --> | Dense Embeddings     |  |
|  | (Apache Tika)      |     | (Sliding Window)    |     | (ONNX Local Runtime) |  |
|  +--------------------+     +---------------------+     +----------+-----------+  |
|                                                                    |              |
|                             +--------------------------------------+              |
|                             v                                                     |
|                +--------------------------+                                       |
|                |   Hybrid Search Engine   |                                       |
|                |  - BM25 Inverted Index   |                                       |
|                |  - H2 Vector Cosine Dist |                                       |
|                +------------+-------------+                                       |
|                             |                                                     |
|                             | (Top-k Chunks + Prompt)                             |
|                             v                                                     |
|                +--------------------------+                                       |
|                |  Ollama Synthesis Engine | ---> [Ollama Daemon: 127.0.0.1:11434] |
|                |  (phi3 / llama3.2)       |      (Zero Outbound Network)          |
|                +--------------------------+                                       |
|                                                                                   |
|  +-----------------------------------------------------------------------------+  |
|  | Network Privacy Guard (0 Outbound Sockets / Air-Gap Verification Monitor)   |  |
|  +-----------------------------------------------------------------------------+  |
+-----------------------------------------------------------------------------------+
```

---

## Core Components

### 1. Document Ingestion & Chunking Pipeline
- **Apache Tika Integration**: Extracts raw text and structural metadata across diverse formats (`.pdf`, `.docx`, `.txt`, `.md`).
- **Semantic Token Chunking**: Slices parsed text into coherent context windows with configurable token overlap to preserve cross-boundary semantic relationships.

### 2. Hybrid Search Engine (BM25 + Dense Vectors)
- **Sparse BM25 Index**: Evaluates exact keyword frequency, term saturation, and inverted document frequency ($IDF$).
- **Dense Vector Search**: Computes Cosine Similarity against normalized 384-dimensional embeddings stored in an embedded H2 vector table:
  $$\text{Cosine Similarity} = \frac{\mathbf{u} \cdot \mathbf{v}}{\|\mathbf{u}\|_2 \|\mathbf{v}\|_2}$$
- **Score Fusion**: Normalizes and weights keyword and semantic scores to return high-precision results even under vocabulary mismatch.

### 3. PCA 2D Embedding Space Visualizer
- **Matrix-Free Power Iteration**: Dynamically computes the top two principal components (PC1, PC2) across active vector embeddings in sub-millisecond time.
- **Interactive Canvas**: High-contrast rendering on `#0d1117` background with cluster category halos, pan/zoom transformation matrices, and click-to-inspect provenance modals.

### 4. Local Synthesis & Citations
- **Grounded Prompt Construction**: Injects retrieved document passages into a strict system prompt targeting the local Ollama instance (`127.0.0.1:11434`).
- **Source Provenance**: Every generated response links directly to citation cards displaying file path, slide/page number, similarity score, and raw text excerpts.

---

## Prerequisites

- **Java Development Kit (JDK)**: Version 21 or later
- **Apache Maven**: Version 3.8+
- **Ollama**: Installed and running locally
  ```powershell
  # Download and run phi-3 locally
  ollama run phi3
  ```

---

## Getting Started

### 1. Clone the Repository
```bash
git clone https://github.com/your-username/local-ai-indexer.git
cd local-ai-indexer
```

### 2. Verify Air-Gapped LLM Service
Ensure Ollama is running and listening on localhost:
```powershell
curl http://127.0.0.1:11434
# Returns: "Ollama is running"
```

### 3. Build & Run the Backend
In your primary terminal, start the Spring Boot service:
```powershell
mvn clean compile
mvn spring-boot:run
```

### 4. Launch the JavaFX Frontend
In a secondary terminal, launch the desktop interface:
```powershell
mvn javafx:run
```

---

## Testing & Verification

Execute the automated test suite, which includes unit tests, UI smoke verifications, and RAG retrieval evaluations:

```powershell
mvn test
```

### Evaluation Metrics
The system incorporates an evaluation harness measuring retrieval performance against ground-truth technical document pairs:
- **Recall@k**: Evaluates whether the ground-truth chunk is retrieved within the top $k$ matches.
- **Mean Reciprocal Rank (MRR)**: Measures the positional rank of the first relevant document chunk.
- **Latency Benchmarks**: End-to-end ingestion and query latencies tracked across parsing, tokenization, vector calculation, and synthesis stages.

---

## Configuration

Application configuration is managed via `src/main/resources/application.properties`:

```properties
# Backend Binding & Air-Gap Enforcement
server.port=8080
server.address=127.0.0.1

# Ollama Synthesis Settings
ollama.base-url=http://127.0.0.1:11434
ollama.model=phi3
ollama.timeout=120s

# Hybrid Search Parameters
rag.search.top-k=6
rag.search.bm25-weight=0.4
rag.search.vector-weight=0.6
```

---

## License

Distributed under the Apache 2.0 License. See `LICENSE` for more information.