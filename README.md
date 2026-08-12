# Society Management - AWS EC2 Deployment Guide

Full-stack Society Management application with Spring Boot backend, Angular frontend, and MySQL database deployed on a single AWS EC2 instance with Nginx reverse proxy.

## Tech Stack

- **Backend:** Spring Boot 3.2.5, Java 17
- **Frontend:** Angular 17
- **Database:** MySQL 8
- **Web Server:** Nginx (reverse proxy + static file server)
- **OS:** Ubuntu 22.04 LTS (on AWS EC2)

---

## 1. Create AWS EC2 Instance

### 1.1 Launch Instance

1. Log into [AWS Console](https://console.aws.amazon.com/ec2/)
2. Click **Launch Instance**
3. Configure:
   - **Name:** `SocietyManagement-Server`
   - **AMI:** Ubuntu Server 22.04 LTS (64-bit x86)
   - **Instance Type:** `t2.medium` (2 vCPU, 4 GB RAM) - minimum recommended for running all three services
   - **Key Pair:** Create new or select existing `.pem` key pair
   - **Storage:** 30 GB gp3 (General Purpose SSD)

### 1.2 Configure Security Group

Create a security group with these inbound rules:

| Type       | Port  | Source    | Purpose              |
|------------|-------|-----------|----------------------|
| SSH        | 22    | Your IP   | SSH access           |
| HTTP       | 80    | 0.0.0.0/0 | Frontend via Nginx   |
| HTTPS      | 443   | 0.0.0.0/0 | HTTPS (optional)     |
| Custom TCP | 8080  | Your IP   | Backend API (debug only, remove in production) |
| Custom TCP | 3306  | Your IP   | MySQL (debug only, remove in production)       |

### 1.3 Allocate Elastic IP (Recommended)

1. Go to **EC2 > Elastic IPs > Allocate Elastic IP address**
2. Associate it with your instance
3. This gives you a static public IP that survives instance restarts

### 1.4 Connect to Instance

```bash
chmod 400 your-key.pem
ssh -i your-key.pem ubuntu@<YOUR_ELASTIC_IP>
```

---

## 2. Initial Server Setup

### 2.1 Update System

```bash
sudo apt update && sudo apt upgrade -y
```

### 2.2 Set Timezone

```bash
sudo timedatectl set-timezone Asia/Kolkata
```

### 2.3 Create Swap Space (recommended for t2.medium)

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

---

## 3. Install MySQL 8

### 3.1 Install

```bash
sudo apt install mysql-server -y
sudo systemctl start mysql
sudo systemctl enable mysql
```

### 3.2 Secure Installation

```bash
sudo mysql_secure_installation
```

Follow the prompts:
- Set root password (e.g., `StrongPassword@123`)
- Remove anonymous users: Yes
- Disallow root login remotely: Yes
- Remove test database: Yes
- Reload privilege tables: Yes

### 3.3 Create Database and User

```bash
sudo mysql -u root -p
```

```sql
CREATE DATABASE society_management CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER 'society_app'@'localhost' IDENTIFIED BY 'YourSecureAppPassword@123';

GRANT ALL PRIVILEGES ON society_management.* TO 'society_app'@'localhost';

FLUSH PRIVILEGES;

EXIT;
```

### 3.4 Verify Connection

```bash
mysql -u society_app -p -D society_management
```

---

## 4. Install Java 17

```bash
sudo apt install openjdk-17-jdk -y
java -version
```

Verify output shows `openjdk version "17.x.x"`.

---

## 5. Install Node.js 18 LTS (for building Angular)

```bash
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt install nodejs -y
node -v
npm -v
```

---

## 6. Install Nginx

```bash
sudo apt install nginx -y
sudo systemctl start nginx
sudo systemctl enable nginx
```

Verify Nginx is running by opening `http://<YOUR_ELASTIC_IP>` in a browser. You should see the Nginx welcome page.

---

## 7. Deploy Backend (Spring Boot)

### 7.1 Create Application Directory

```bash
sudo mkdir -p /opt/society-management/backend
sudo mkdir -p /opt/society-management/uploads
sudo chown -R ubuntu:ubuntu /opt/society-management
```

### 7.2 Clone Repository

```bash
cd /opt/society-management
git clone https://github.com/YOUR_USERNAME/SocietyManagement.git repo
```

### 7.3 Create Production Configuration

```bash
nano /opt/society-management/backend/application-prod.yml
```

Paste:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/society_management?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata
    username: society_app
    password: YourSecureAppPassword@123
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect

  sql:
    init:
      mode: always
      data-locations: classpath:db/data.sql

  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB

server:
  port: 8080
  servlet:
    context-path: /api

logging:
  level:
    com.society: INFO
    org.hibernate.SQL: WARN
  file:
    path: /opt/society-management/logs

app:
  upload:
    dir: /opt/society-management/uploads
  jwt:
    secret: CHANGE_THIS_TO_A_SECURE_RANDOM_STRING_AT_LEAST_64_CHARS_LONG_abc123xyz
    expiration: 86400000
    refresh-expiration: 604800000
  cashfree:
    app-id: YOUR_CASHFREE_APP_ID
    secret-key: YOUR_CASHFREE_SECRET_KEY
    api-version: 2023-08-01
    environment: production
    return-url: http://YOUR_DOMAIN/maintenance/payment-status
    notify-url: http://YOUR_DOMAIN/api/maintenance/payments/webhook
  base-url: http://YOUR_DOMAIN/api
```

### 7.4 Build the Backend

```bash
cd /opt/society-management/repo/backend
./mvnw clean package -DskipTests
```

If `mvnw` is not present or not executable:

```bash
sudo apt install maven -y
mvn clean package -DskipTests
```

### 7.5 Copy JAR to Deployment Directory

```bash
cp target/society-management-1.0.0-SNAPSHOT.jar /opt/society-management/backend/app.jar
```

### 7.6 Create Systemd Service

```bash
sudo nano /etc/systemd/system/society-backend.service
```

Paste:

```ini
[Unit]
Description=Society Management Backend
After=mysql.service
Requires=mysql.service

[Service]
User=ubuntu
Group=ubuntu
WorkingDirectory=/opt/society-management/backend
ExecStart=/usr/bin/java -jar -Xms512m -Xmx1024m -Dspring.profiles.active=prod -Dspring.config.additional-location=file:/opt/society-management/backend/application-prod.yml app.jar
SuccessExitStatus=143
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

### 7.7 Start Backend Service

```bash
sudo systemctl daemon-reload
sudo systemctl start society-backend
sudo systemctl enable society-backend
```

### 7.8 Verify Backend is Running

```bash
sudo systemctl status society-backend
# Check logs
sudo journalctl -u society-backend -f
```

Wait for the application to fully start, then test:

```bash
curl http://localhost:8080/api/api-docs
```

---

## 8. Deploy Frontend (Angular)

### 8.1 Update Environment for Production

Edit the production environment file before building:

```bash
cd /opt/society-management/repo/frontend
nano src/environments/environment.prod.ts
```

Set:

```typescript
export const environment = {
  production: true,
  apiUrl: '/api'  // Nginx will proxy this to backend
};
```

### 8.2 Install Dependencies and Build

```bash
npm install
npx ng build --configuration production
```

### 8.3 Deploy Built Files to Nginx

```bash
sudo mkdir -p /var/www/society-management
sudo cp -r dist/society-management-frontend/browser/* /var/www/society-management/
sudo chown -R www-data:www-data /var/www/society-management
```

> Note: The Angular output directory may vary. Check `dist/` folder structure after build. It could be `dist/society-management-frontend/` or `dist/browser/`. Adjust the path accordingly.

---

## 9. Configure Nginx

### 9.1 Create Nginx Server Block

```bash
sudo nano /etc/nginx/sites-available/society-management
```

Paste:

```nginx
server {
    listen 80;
    server_name YOUR_DOMAIN_OR_IP;

    # Frontend - Angular SPA
    root /var/www/society-management;
    index index.html;

    # Gzip compression
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml text/javascript;
    gzip_min_length 1000;

    # Backend API proxy
    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 90s;
        proxy_connect_timeout 90s;
        client_max_body_size 10M;
    }

    # Uploaded files
    location /api/files/ {
        proxy_pass http://localhost:8080/api/files/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # Angular SPA - all other routes fallback to index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Cache static assets
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

### 9.2 Enable the Site

```bash
sudo ln -s /etc/nginx/sites-available/society-management /etc/nginx/sites-enabled/
sudo rm /etc/nginx/sites-enabled/default
```

### 9.3 Test and Reload Nginx

```bash
sudo nginx -t
sudo systemctl reload nginx
```

---

## 10. SSL/HTTPS Setup with Let's Encrypt (Optional but Recommended)

If you have a domain name pointing to your EC2 IP:

```bash
sudo apt install certbot python3-certbot-nginx -y
sudo certbot --nginx -d yourdomain.com
```

Certbot will automatically configure Nginx for HTTPS and set up auto-renewal.

Test auto-renewal:

```bash
sudo certbot renew --dry-run
```

---

## 11. Firewall Setup (UFW)

```bash
sudo ufw allow OpenSSH
sudo ufw allow 'Nginx Full'
sudo ufw enable
sudo ufw status
```

---

## 12. Useful Commands

### Service Management

```bash
# Backend
sudo systemctl start society-backend
sudo systemctl stop society-backend
sudo systemctl restart society-backend
sudo systemctl status society-backend

# Nginx
sudo systemctl restart nginx
sudo nginx -t

# MySQL
sudo systemctl status mysql
```

### View Logs

```bash
# Backend logs
sudo journalctl -u society-backend -f --lines=100

# Nginx access logs
sudo tail -f /var/log/nginx/access.log

# Nginx error logs
sudo tail -f /var/log/nginx/error.log

# MySQL logs
sudo tail -f /var/log/mysql/error.log
```

### Redeploy Backend

```bash
cd /opt/society-management/repo
git pull origin main
cd backend
mvn clean package -DskipTests
cp target/society-management-1.0.0-SNAPSHOT.jar /opt/society-management/backend/app.jar
sudo systemctl restart society-backend
```

### Redeploy Frontend

```bash
cd /opt/society-management/repo/frontend
git pull origin main
npm install
npx ng build --configuration production
sudo rm -rf /var/www/society-management/*
sudo cp -r dist/society-management-frontend/browser/* /var/www/society-management/
sudo systemctl reload nginx
```

---

## 13. Monitoring and Maintenance

### Check Disk Space

```bash
df -h
```

### Check Memory Usage

```bash
free -m
htop
```

### MySQL Backup (Cron Job)

```bash
sudo nano /opt/society-management/backup.sh
```

```bash
#!/bin/bash
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/opt/society-management/backups"
mkdir -p $BACKUP_DIR
mysqldump -u society_app -pYourSecureAppPassword@123 society_management > $BACKUP_DIR/society_mgmt_$TIMESTAMP.sql
# Keep only last 7 days of backups
find $BACKUP_DIR -name "*.sql" -mtime +7 -delete
```

```bash
chmod +x /opt/society-management/backup.sh
```

Add to cron (daily at 2 AM):

```bash
crontab -e
```

Add line:

```
0 2 * * * /opt/society-management/backup.sh
```

---

## 14. Troubleshooting

| Problem | Solution |
|---------|----------|
| Backend won't start | Check logs: `sudo journalctl -u society-backend -f` |
| MySQL connection refused | Verify MySQL is running: `sudo systemctl status mysql` |
| Nginx 502 Bad Gateway | Backend isn't running or wrong port in proxy_pass |
| Frontend blank page | Check `try_files` in Nginx config, verify files exist in `/var/www/society-management/` |
| Out of memory | Reduce Java heap: `-Xmx512m`, or upgrade instance type |
| Permission denied | Check file ownership: `ls -la /var/www/society-management/` |

---

## 15. Estimated AWS Costs (Mumbai Region - ap-south-1)

| Resource | Monthly Cost (approx) |
|----------|----------------------|
| EC2 t2.medium (On-Demand) | ~$27/month |
| EBS 30 GB gp3 | ~$2.50/month |
| Elastic IP (associated) | Free |
| Data Transfer (first 100 GB) | ~$9/month |
| **Total** | **~$38-40/month** |

> Tip: Use a Reserved Instance (1-year) to save ~40% on EC2 costs.

---

## Architecture Diagram

```
                    Internet
                       |
                  [Elastic IP]
                       |
              +--------+--------+
              |   EC2 Instance  |
              |  (t2.medium)    |
              +--------+--------+
                       |
              +--------+--------+
              |     Nginx:80    |
              |  (Reverse Proxy)|
              +---+--------+----+
                  |        |
          /api/*  |        |  /*
                  v        v
         +--------+--+  +--+------------+
         | Spring    |  | Angular SPA   |
         | Boot:8080 |  | /var/www/...  |
         +--------+--+  +---------------+
                  |
                  v
         +--------+--+
         | MySQL:3306|
         | society_  |
         | management|
         +-----------+
```
