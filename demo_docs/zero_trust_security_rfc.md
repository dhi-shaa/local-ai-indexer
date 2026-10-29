# Enterprise Security Baseline: Zero Trust Network Architecture (ZTNA)
Department: Information Security Operations
Status: Approved RFC-8941

## 1. Microsegmentation Policies
All internal VPC communications require Mutual TLS (mTLS 1.3) with ephemeral X.509 certificates issued via HashiCorp Vault.
Default perimeter access assumes an adversarial environment where network locality does not confer trust.

## 2. Service-to-Service Authorization
Services authenticate using SPIFFE/SPIRE IDs embedded in Subject Alternative Names (SANs).
Access control is evaluated at every hop via an Open Policy Agent (OPA) sidecar proxy executing declarative Rego rules.

## 3. Key Rotation & Cryptographic Standards
- Symmetric payload encryption: AES-256-GCM.
- Key exchange: X25519 with Ed25519 signatures.
- Root Certificate Authority (CA) rotation occurs every 90 days; leaf certificates expire after 24 hours.
