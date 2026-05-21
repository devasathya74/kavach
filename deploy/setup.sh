#!/bin/bash

# KAVACH VPS SETUP SCRIPT (Ubuntu 22.04+)
# Use: sudo bash setup.sh

set -e

echo "--- 🛡️ KAVACH SYSTEM DEPLOYMENT INITIATED ---"

# 1. Update & Install Dependencies
apt-get update
apt-get install -y apt-transport-https ca-certificates curl software-properties-common gnupg-agent

# 2. Install Docker
if ! command -v docker &> /dev/null; then
    echo "Installing Docker..."
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | apt-key add -
    add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable"
    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io
fi

# 3. Install Docker Compose
if ! command -v docker-compose &> /dev/null; then
    echo "Installing Docker Compose..."
    curl -L "https://github.com/docker/compose/releases/download/v2.20.2/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    chmod +x /usr/local/bin/docker-compose
fi

# 4. Prepare Environment
mkdir -p /opt/kavach/nginx/conf.d
cp docker-compose.yml /opt/kavach/
cd /opt/kavach

# Create base Nginx config
cat <<EOF > nginx/conf.d/default.conf
server {
    listen 80;
    server_name _;

    location /static/ {
        alias /app/static/;
    }

    location /media/ {
        alias /app/media/;
    }

    location / {
        proxy_pass http://backend:8000;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
EOF

# 5. Launch
echo "Starting KAVACH Ecosystem..."
docker-compose up -d

echo "--- ✅ DEPLOYMENT COMPLETE ---"
echo "System reachable on port 80."
