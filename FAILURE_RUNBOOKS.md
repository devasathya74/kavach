# KAVACH Failure Runbooks - Human Procedures

This document defines the deterministic steps for battalion operators to recover the platform from critical failures. **Trust human procedures over autonomous recovery.**

---

## 1. Database Outage (PostgreSQL Down)
**Symptoms**: All dashboard actions fail with "503 Service Unavailable" or "Database Connection Refused".
1. **Verify Service**: Run `Get-Service | Where-Object { $_.DisplayName -like "*PostgreSQL*" }`.
2. **Restart**: If status is `Stopped`, right-click -> Start in `services.msc`.
3. **Verify Port**: Run `netstat -ano | findstr :5432`. Ensure it is `LISTENING`.
4. **Log Check**: Inspect `logs/kavach_operational.log` for connection pool exhaustion.

## 2. Communication Failure (Ngrok Tunnel Down)
**Symptoms**: Android clients show "Network Unstable" or "Offline" even with active internet.
1. **Check Tunnel**: Visit the ngrok dashboard or hit `/health/live/` via public URL.
2. **Restart Tunnel**: Kill the ngrok process and restart: `ngrok http 8000`.
3. **Update Client**: If the URL changed, provide the new endpoint to field officers.

## 3. High Outbox Backlog
**Symptoms**: `/health/deep/` reports `outbox_backlog` > 10,000. Mobile clients are not receiving real-time updates.
1. **Verify Workers**: Ensure Celery/Task-Runner is active.
2. **Emergency Cleanup**: If backlog is due to junk/failed events, run:
   ```powershell
   python manage.py operational_cleanup
   ```

## 4. Device Compromise / Lost Phone
**Symptoms**: Officer reports a missing or stolen mission device.
1. **Revoke Session**: In the Device Center, find the device and set `status = REVOKED`.
2. **Reset Credentials**: Immediately reset the Officer's password and clear all active JWTs from the cache.

## 5. Offline Sync Conflict
**Symptoms**: DraftChange status is `CONFLICTED` due to revision drift.
1. **Manual Merge**: Compare `old_value`, `new_value`, and `expected_state`.
2. **Resolution**: The Commanding Officer (CO) must manually override or re-submit the change.

## 6. PostgreSQL Corruption
**Symptoms**: DB logs report checksum failures or table corruption.
1. **Stop Server**: Immediately halt the Django process.
2. **Restore**: Use the automated script or manual dump.
   ```powershell
   # Automated restore from latest backup
   psql -U kavach_user -d kavach_db < backups\kavach_backup_latest.sql
   ```
3. **Verify**: Run `python manage.py check` before restarting.
4. **Maintenance**: Regularly run `python manage.py operational_cleanup` to monitor "Dead Tuples" and reclaim storage.

---

## 7. Operational Maintenance (Weekly)
1. **Backup Verification**: Ensure `backups/` contains 7 days of `.sql` files.
2. **Bloat Audit**: Run `python manage.py operational_cleanup`. If "Dead Tuples" exceed 1,000,000 on active tables, schedule a `VACUUM FULL`.
3. **Tunnel Rotation**: If using ngrok, verify tunnel stability and update the base URL.

---

## 8. Chaos Test Protocol (Monthly Drill)
Perform these tests with real humans on real devices to verify "Field Presence".
1. **The Tunnel Collapse**: Kill the ngrok process mid-broadcast.
   - *Requirement*: Clients must show "Network Unstable" immediately and retry with backoff.
2. **The Rogue Device**: Revoke an active officer's device session in the Admin Panel.
   - *Requirement*: The app must immediately halt and show the "Security Restriction" screen.
3. **The Sync War**: Modify the same Incident Draft on two devices simultaneously while offline.
   - *Requirement*: The backend must detect the conflict and flag it for CO Review (DraftChange: CONFLICTED).
4. **The Dead-Zone Endurance**: Create 10+ incidents in Airplane Mode and then restore signal.
   - *Requirement*: Outbox must drain sequentially without payload corruption or duplicate events.
