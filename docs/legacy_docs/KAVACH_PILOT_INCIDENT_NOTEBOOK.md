# KAVACH Pilot Incident Notebook

**Pilot Philosophy:**
> "Controlled behavior under imperfect conditions."
> Do NOT tune based on single incidents. A behavior change requires the same issue, same signature, same reproduction path, and minimum 3 independent occurrences.
> Separate **Security Confidence** from **Operational Confidence**. If incidents fall mostly under HUMAN, OEM, or NETWORK, the issue is the ecosystem, not the auth architecture. Do NOT change the architecture.
> **Observation over Intervention:** If an issue is R0, R1, C0, or C1 -> the correct action is observation, not intervention. Avoid panic-driven tuning.
> **First 14 Days Rule:** Zero architecture tuning. Zero threshold changes. Zero retry policy changes. Only observe, classify, correlate, measure.

## Pilot Success Criteria
| Metric                  | Target   |
| ----------------------- | -------- |
| Forced relogin/day      | < 0.5    |
| Silent recovery success | > 95%    |
| S1 incidents            | 0        |
| R3 OEM failures         | < 2 OEMs |
| Support dependency rate | < 5%     |
| Mission completion      | > 98%    |
| Mean time to operator confidence | < 14 Days (Users stop complaining) |

## Incident Closure Rule
No incident can be marked `RESOLVED` unless:
1. Root cause confidence ≥ C3
2. Mitigation validated
3. No repeat for 7 days
4. Mission completion unaffected
Otherwise, it remains `INVESTIGATING`. Premature "resolved" tags destroy future debugging.

## Incident Classification Categories
* **SECURITY**: Real tamper / suspicious activity
* **NETWORK**: Packet loss / timeout / unstable connection
* **OEM**: Vendor battery killer / background execution limit
* **UX**: Confusing flow / officer frustration
* **HUMAN**: Officer mistake (sharing credentials, wrong clock)
* **CONFIG**: Deployment / Server configuration mistake

## Incident Severity
| Severity | Meaning                         |
| -------- | ------------------------------- |
| **S0**   | Security breach / data exposure |
| **S1**   | Complete operational outage     |
| **S2**   | Officer blocked but recoverable |
| **S3**   | UX friction / delay             |
| **S4**   | Cosmetic / harmless             |

## Reproducibility Score
| Score  | Meaning                   |
| ------ | ------------------------- |
| **R0** | Never reproduced          |
| **R1** | Single device only        |
| **R2** | Multiple devices same OEM |
| **R3** | Cross-OEM reproducible    |
| **R4** | Deterministic             |

## Incident Confidence
| Confidence | Meaning                 |
| ---------- | ----------------------- |
| **C0**     | Pure assumption         |
| **C1**     | Weak evidence           |
| **C2**     | Logs support hypothesis |
| **C3**     | Reproduced internally   |
| **C4**     | Root cause confirmed    |

## Recovery Cost
| Cost   | Meaning                |
| ------ | ---------------------- |
| **RC0**| Invisible recovery     |
| **RC1**| Officer waited         |
| **RC2**| Officer retried        |
| **RC3**| Officer relogin        |
| **RC4**| Officer needed support |

---

## 📋 Active Incidents Log

### Template
**Date/Time:** YYYY-MM-DD HH:MM
**Category:** [SECURITY | NETWORK | OEM | UX | HUMAN | CONFIG]
**Severity:** [S0 | S1 | S2 | S3 | S4]
**Reproducibility:** [R0 | R1 | R2 | R3 | R4]
**Confidence:** [C0 | C1 | C2 | C3 | C4]
**Recovery Cost:** [RC0 | RC1 | RC2 | RC3 | RC4]
**Correlation ID:** `uuid-here`
**Device Details:**
- OEM: `Manufacturer`
- Android Version: `SDK_INT`
- App Version: `1.0.x`
- Network: `Jio 4G / WiFi`
**Symptom:** 
> Short description of what happened from the user's perspective (e.g., stuck on verifying spinner).
**Integrity State Transition:** 
> e.g. STRONG -> DEVICE -> DEGRADED -> RECOVERED
**Did the officer complete the mission?** [ YES | NO | PARTIALLY ]
**Officer Workaround Used:** 
> e.g. force close app, switch to WiFi, reinstall APK, relogin.
**Resolution / Investigation:**
> What the logs showed, and the outcome (e.g., Vivo battery optimization killed the token refresh).
**Status:** [ INVESTIGATING | RESOLVED | WON'T FIX (OEM LIMITATION) ]

---

### Incident #001
**Date/Time:** 
**Category:** 
**Severity:** 
**Reproducibility:** 
**Correlation ID:** 
**Device Details:**
- OEM: 
- Android Version: 
- App Version: 
- Network: 
**Symptom:** 
> 
**Integrity State Transition:**
> 
**Did the officer complete the mission?** 
**Resolution / Investigation:**
> 
**Status:** 
