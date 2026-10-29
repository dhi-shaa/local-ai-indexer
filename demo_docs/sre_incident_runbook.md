# Emergency Runbook: SEV-1 Production Incident Response
Classification: Internal SRE Documentation

## 1. Initial Triage & War Room Escalation
Upon receiving a PagerDuty alert exceeding the 99.9th percentile error budget:
1. Incident Commander (IC) establishes an encrypted bridge within 3 minutes.
2. Scribe starts an append-only timeline document.
3. Communications Lead posts initial advisory to status.internal within 10 minutes.

## 2. Database Failover Runbook
If primary PostgreSQL cluster latency exceeds 5000ms:
- Verify replication lag on replica-01 via pg_stat_replication.
- If lag < 50ms, issue pg_ctl promote to execute immediate leader election.
- Update DNS alias db-master.prod to point to replica-01 IP.

## 3. Root Cause Analysis (RCA) Policy
A blameless post-mortem must be drafted within 48 business hours, identifying:
- Contributing factors (not human blame).
- Time to Detect (TTD) and Time to Mitigate (TTM).
- Action items tracked in JIRA with target completion under 14 days.
