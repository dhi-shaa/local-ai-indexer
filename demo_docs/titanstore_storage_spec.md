# Internal Architecture: TitanStore Distributed KV Engine
Author: Core Storage Engineering Group
Date: September 2026

## 1. Storage Architecture Overview
TitanStore is an LSM-tree (Log-Structured Merge-tree) based distributed key-value store engineered for write-heavy analytical workloads. 
Write operations append to an immutable commit-log on NVMe storage before updating an in-memory MemTable backed by a skip-list.

## 2. Compaction Strategies
To control read amplification and space usage, TitanStore implements Leveled Compaction:
- Level 0 (L0): Holds flushed MemTables. SSTables may have overlapping key ranges.
- Level 1-6: Strictly non-overlapping key ranges. Each level is 10x larger than the previous.
- Compaction Trigger: Occurs when L0 file count exceeds 4 or Level N size limit is breached.

## 3. Consensus and Replication
Data replication uses the Raft consensus algorithm with a quorum size of (N/2) + 1. 
Heartbeat intervals are pinned at 150ms with randomized election timeouts between 300ms and 600ms.
