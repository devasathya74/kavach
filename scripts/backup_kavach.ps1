# KAVACH Automated Backup Script
# Purpose: Nightly pg_dump with local 7-day retention.

$DB_NAME = "kavach_db"
$DB_USER = "kavach_user"
$BACKUP_DIR = "C:\Users\CO_25\Desktop\kavach\backups"
$DATE = Get-Date -Format "yyyy-MM-dd_HH-mm"
$BACKUP_FILE = "$BACKUP_DIR\kavach_backup_$DATE.sql"

# 1. Create backup directory if not exists
if (!(Test-Path $BACKUP_DIR)) {
    New-Item -ItemType Directory -Path $BACKUP_DIR
}

Write-Host "--- Starting Backup for $DB_NAME ---"

# 2. Execute pg_dump
try {
    & pg_dump -U $DB_USER -h 127.0.0.1 -d $DB_NAME -f $BACKUP_FILE
    if ($LASTEXITCODE -eq 0) {
        Write-Host "SUCCESS: Backup saved to $BACKUP_FILE"
    } else {
        Write-Error "FAILURE: pg_dump failed with exit code $LASTEXITCODE"
    }
} catch {
    Write-Error "FAILURE: An error occurred during backup: $_"
}

# 3. Enforce 7-Day Retention (Delete older files)
Write-Host "Cleaning up backups older than 7 days..."
Get-ChildItem -Path $BACKUP_DIR -Filter "*.sql" | Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-7) } | Remove-Item -Force
Write-Host "Cleanup complete."

# 4. Monthly Verification Drill (Manual/Scheduled)
# Note: Every month, manually run a test restore to verify backup integrity.
# $TEST_DB = "kavach_db_verify"
# try {
#     & createdb -U $DB_USER $TEST_DB
#     & psql -U $DB_USER -d $TEST_DB -f $BACKUP_FILE
#     if ($LASTEXITCODE -eq 0) { Write-Host "VERIFIED: Backup is structurally sound." }
#     & dropdb -U $DB_USER $TEST_DB
# } catch { Write-Error "VERIFICATION FAILED: $_" }
