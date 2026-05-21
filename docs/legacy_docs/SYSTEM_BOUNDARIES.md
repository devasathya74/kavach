# KAVACH System Boundaries

This document defines the operational scope of the KAVACH platform. To prevent "Philosophy Inflation" and ensure long-term survivability, all future features must remain within these boundaries.

---

## ✅ WHAT KAVACH DOES
1. **Secure Coordination**: Real-time transmission of mission-critical orders and status updates between the Command Center and Field Officers.
2. **Deterministic Audit Trail**: Maintaining an immutable, indexed ledger of all officer activities and state changes for forensic accountability.
3. **Device Control**: Enforcement of Zero-Trust security policies, device attestation, and remote revocation.
4. **Incident Reporting**: Structured collection of field data (GPS, Media, Narratives) with chain-of-custody integrity.
5. **Offline Resilience**: Reliable synchronization of field drafts and state revisions across intermittent networks.

---

## 🚫 WHAT KAVACH DOES NOT DO
1. **Autonomous Command**: The platform NEVER issues orders or makes mission decisions without explicit human authorization.
2. **Strategic Intelligence**: KAVACH is a coordination layer, not an AI-driven strategy engine. It does not "think" or "plan".
3. **Predictive Policing**: The platform does not use historical data to predict future events or profile individuals.
4. **Truth Arbitration**: The system records "Field Reality" as reported; it does not attempt to verify truth through autonomous analysis.
5. **Battlefield AI**: No kinetic or tactical automation is integrated into the core platform logic.
6. **Human Replacement**: The system is a tool for human authority, not a replacement for institutional judgment.

---

## 🛡️ OPERATIONAL FENCES
* **Abstraction Freeze**: New architectural layers are prohibited. Expand through components, not abstractions.
* **Semantic Freeze**: Use simple wording (Approvals, Analysis, Recovery) to prevent maintainer confusion.
* **Complexity Budget**: Every new database model must be justified by an **Execution Requirement**, not a conceptual one.
