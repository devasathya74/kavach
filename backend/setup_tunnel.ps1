# KAVACH - CLOUDFLARE TUNNEL AUTOMATED SETUP SCRIPT
# Run this script in PowerShell as Administrator

$ErrorActionPreference = "Stop"
Clear-Host

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "         KAVACH CLOUDFLARE TUNNEL SETUP UTILITY" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "[+] Local User: Kavach"
Write-Host "[+] Target Domain: api.pmsraebareli.online"
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

$cfDir = "C:\Users\kavach\.cloudflared"
$certPath = "$cfDir\cert.pem"

# Create directory if it doesn't exist
if (!(Test-Path $cfDir)) {
    New-Item -ItemType Directory -Path $cfDir -Force | Out-Null
    Write-Host "[+] Created configuration directory: $cfDir" -ForegroundColor Green
}

# --- STEP 1 & 2: LOGIN / AUTHENTICATION ---
if (!(Test-Path $certPath)) {
    Write-Host "[!] Cloudflare certificate not found at $certPath" -ForegroundColor Yellow
    Write-Host "[*] Initiating browser login. Please authorize the 'pmsraebareli.online' domain in your browser." -ForegroundColor Cyan
    Write-Host "[*] Running: cloudflared tunnel login..." -ForegroundColor Gray
    
    # Launch login process in the user's active session
    Start-Process "cloudflared" -ArgumentList "tunnel login" -Wait
    
    Write-Host ""
    Write-Host "[*] Waiting for cert.pem to be authorized and written..." -ForegroundColor Cyan
    
    # Loop and wait for the cert.pem file to appear
    while (!(Test-Path $certPath)) {
        Start-Sleep -Seconds 3
        Write-Host "    --> Waiting for browser authorization..." -ForegroundColor Gray
    }
    Write-Host "[+] Authentication successful! cert.pem detected." -ForegroundColor Green
} else {
    Write-Host "[+] Authentication certificate already exists at $certPath" -ForegroundColor Green
}

# --- STEP 3: CREATE TUNNEL ---
Write-Host ""
Write-Host "[*] Checking existing tunnels..." -ForegroundColor Cyan

# Find if kavach-api already exists, or create a new one
$tunnelUuid = $null
$tunnels = & "C:\Program Files (x86)\cloudflared\cloudflared.exe" tunnel list

if ($tunnels -match "kavach-api") {
    Write-Host "[+] Tunnel 'kavach-api' already exists!" -ForegroundColor Green
    # Extract UUID from the tunnel list output
    foreach ($line in ($tunnels -split "`n")) {
        if ($line -match "kavach-api") {
            # Line format is usually: <UUID> kavach-api <Created> ...
            $parts = $line.Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries)
            if ($parts.Count -gt 0) {
                $tunnelUuid = $parts[0].Trim()
                Write-Host "[+] Extracted Tunnel UUID: $tunnelUuid" -ForegroundColor Green
                break
            }
        }
    }
}

if (!$tunnelUuid) {
    Write-Host "[*] Creating new tunnel: kavach-api..." -ForegroundColor Cyan
    $createOutput = & "C:\Program Files (x86)\cloudflared\cloudflared.exe" tunnel create kavach-api
    Write-Host $createOutput -ForegroundColor Gray
    
    # Parse UUID from output
    if ($createOutput -match "Created tunnel kavach-api with id ([a-f0-9\-]+)") {
        $tunnelUuid = $Matches[1]
        Write-Host "[+] Created Tunnel UUID: $tunnelUuid" -ForegroundColor Green
    } else {
        # Fallback: check the .cloudflared folder for json files
        $jsonFiles = Get-ChildItem -Path $cfDir -Filter "*.json"
        if ($jsonFiles.Count -eq 1) {
            $tunnelUuid = $jsonFiles[0].BaseName
            Write-Host "[+] Found Tunnel UUID from credentials file: $tunnelUuid" -ForegroundColor Green
        } else {
            Write-Error "Could not determine tunnel UUID. Please check your cloudflared installation."
        }
    }
}

# --- STEP 4: BIND DOMAIN DNS ---
Write-Host ""
Write-Host "[*] Binding domain DNS for api.pmsraebareli.online..." -ForegroundColor Cyan
try {
    $dnsOutput = & "C:\Program Files (x86)\cloudflared\cloudflared.exe" tunnel route dns kavach-api api.pmsraebareli.online
    Write-Host "[+] DNS route registered successfully!" -ForegroundColor Green
} catch {
    Write-Host "[!] Routing warning (it might already be routed): $_" -ForegroundColor Yellow
}

# --- STEP 5: CREATE CONFIG.YML ---
Write-Host ""
Write-Host "[*] Creating configuration file: $cfDir\config.yml..." -ForegroundColor Cyan

$configContent = @"
tunnel: $tunnelUuid
credentials-file: C:\Users\kavach\.cloudflared\$tunnelUuid.json

ingress:
  - hostname: api.pmsraebareli.online
    service: http://localhost:8000

  - service: http_status:404
"@

[System.IO.File]::WriteAllText("$cfDir\config.yml", $configContent)
Write-Host "[+] Created config.yml successfully with correct paths!" -ForegroundColor Green
Write-Host "----------------------------------------------------------" -ForegroundColor Gray
Write-Host $configContent -ForegroundColor DarkGray
Write-Host "----------------------------------------------------------" -ForegroundColor Gray

# --- STEP 8: PREPARE SERVICE INSTALLATION ---
Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "        CLOUDFLARE TUNNEL SETUP COMPLETED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "[+] Tunnel Name:  kavach-api"
Write-Host "[+] Tunnel UUID:  $tunnelUuid"
Write-Host "[+] Public URL:   https://api.pmsraebareli.online"
Write-Host "==========================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Next Steps for Permanent Service (Run in Admin PowerShell):" -ForegroundColor Yellow
Write-Host "1. Install as Windows Service:" -ForegroundColor Cyan
Write-Host "   cloudflared service install" -ForegroundColor Gray
Write-Host ""
Write-Host "2. Start the Windows Service:" -ForegroundColor Cyan
Write-Host "   Start-Service cloudflared" -ForegroundColor Gray
Write-Host ""
Write-Host "3. Run your Kavach Stack Launcher Bat script!" -ForegroundColor Cyan
Write-Host ""
Write-Host "Press any key to close this installer..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
