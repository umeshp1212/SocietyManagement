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

---

## 16. Cashfree Payment Gateway - Setup and Integration

Cashfree is used in this project to generate online payment links for maintenance bills. Owners receive a payment link (via WhatsApp or SMS) and can pay directly using UPI, cards, or net banking.

### 16.1 Create Cashfree Account

1. Go to [https://merchant.cashfree.com/merchants/signup](https://merchant.cashfree.com/merchants/signup)
2. Sign up with your email and phone number
3. Complete KYC verification:
   - Business PAN card
   - GST certificate (if applicable)
   - Bank account details (for settlement)
   - Address proof
4. Wait for approval (usually 1-2 business days)

### 16.2 Get API Credentials

#### Sandbox (Testing)

1. Log into [Cashfree Dashboard](https://merchant.cashfree.com)
2. Go to **Developers > API Keys**
3. Switch to **Sandbox** mode (toggle at top)
4. Copy:
   - **App ID** (e.g., `TEST1234567890abc`)
   - **Secret Key** (e.g., `cfsk_ma_test_xxxxxxxxxxxx`)

#### Production (Live)

1. Same dashboard, switch to **Production** mode
2. Generate production API keys after KYC approval
3. Copy:
   - **App ID**
   - **Secret Key**

### 16.3 Configure Webhook

1. In Cashfree Dashboard, go to **Developers > Webhooks**
2. Add a new webhook:
   - **URL:** `https://YOUR_DOMAIN/api/maintenance/payments/webhook`
   - **Events:** Select `ORDER_PAID`, `PAYMENT_SUCCESS`, `PAYMENT_FAILED`
   - **Version:** 2023-08-01
3. Note down the webhook secret for verification (optional)

### 16.4 Application Configuration

Update the `application-prod.yml` on your EC2 instance:

```yaml
app:
  cashfree:
    app-id: YOUR_PRODUCTION_APP_ID
    secret-key: YOUR_PRODUCTION_SECRET_KEY
    api-version: 2023-08-01
    environment: production
    return-url: https://YOUR_DOMAIN/maintenance/payment-status
    notify-url: https://YOUR_DOMAIN/api/maintenance/payments/webhook
```

Restart the backend after updating:

```bash
sudo systemctl restart society-backend
```

### 16.5 How It Works in the Application

```
Owner views bill → Clicks "Pay Online" → Backend calls Cashfree API
                                             ↓
                                    Payment link generated
                                             ↓
                              Owner redirected to Cashfree checkout
                                             ↓
                                    Owner completes payment
                                             ↓
                              Cashfree sends webhook to backend
                                             ↓
                              Backend updates bill status to PAID
```

**API Endpoints:**

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/maintenance/bills/{billId}/payment-link` | POST | Generate payment link for a bill |
| `/api/maintenance/payments/webhook` | POST | Receives Cashfree payment notifications |
| `/api/maintenance/payments/status/{orderId}` | GET | Check payment status |
| `/api/maintenance/bills/{billId}/whatsapp-link` | GET | Generate WhatsApp share link with payment URL |

### 16.6 Testing with Sandbox

Use these test credentials in sandbox mode:

| Payment Method | Test Details |
|---------------|--------------|
| UPI | Use any VPA like `testsuccess@gocash` |
| Card (Success) | Card: `4111111111111111`, Expiry: any future date, CVV: `123` |
| Card (Failure) | Card: `4111111111111112` |
| Net Banking | Select any bank, click "Success" on test page |

### 16.7 Settlement

- Cashfree settles payments to your bank account
- Default settlement cycle: T+1 (next business day)
- Check settlements in: **Dashboard > Settlements**
- Cashfree charges: ~2% per transaction (varies by plan)

---

## 17. WhatsApp Business - Setup and Integration

This project uses WhatsApp to share maintenance bill payment links with unit owners. It uses the `wa.me` deep link approach (click-to-chat) which works without the paid WhatsApp Business API.

### 17.1 Option A: WhatsApp Click-to-Chat Links (Current Implementation - Free)

The current implementation uses WhatsApp's `wa.me` deep links which require NO API setup. When a society admin clicks "Share via WhatsApp" on a bill, it opens WhatsApp with a pre-filled message containing the payment link.

**How it works:**
- Backend generates a URL: `https://wa.me/91XXXXXXXXXX?text=<encoded_message>`
- Frontend opens this URL which launches WhatsApp (web or mobile)
- Message is pre-filled with bill details and payment link
- Admin manually sends it to the owner

**No setup required** — this works out of the box once Cashfree payment links are configured.

### 17.2 Option B: WhatsApp Business API (For Automated Messages)

If you want to send automated WhatsApp messages (no manual intervention), you need the WhatsApp Business API via Meta's Cloud API or a BSP (Business Solution Provider).

#### Step 1: Create Meta Business Account

1. Go to [Meta Business Suite](https://business.facebook.com/)
2. Create a new business account or use existing
3. Complete business verification (may take 1-7 days)

#### Step 2: Set Up WhatsApp Business Platform

1. Go to [Meta for Developers](https://developers.facebook.com/)
2. Click **My Apps > Create App**
3. Select **Business** type
4. Add the **WhatsApp** product to your app
5. Get a **Test Phone Number** (Meta provides one free for testing)

#### Step 3: Get API Credentials

1. In the WhatsApp section of your app, go to **API Setup**
2. Note down:
   - **Phone Number ID**: identifies your WhatsApp business number
   - **WhatsApp Business Account ID**
   - **Temporary Access Token** (for testing)
3. For production, generate a **Permanent Access Token**:
   - Go to **Business Settings > System Users**
   - Create a system user with `whatsapp_business_messaging` permission
   - Generate a token for that system user

#### Step 4: Register a Phone Number

1. Get a dedicated phone number for your society (not used in personal WhatsApp)
2. In Meta Developer Dashboard > WhatsApp > **Phone Numbers** > Add
3. Verify via SMS or voice call
4. This number will be the "sender" for all automated messages

#### Step 5: Create Message Templates

WhatsApp requires pre-approved templates for business-initiated messages.

1. Go to **WhatsApp Manager > Message Templates**
2. Create a template:

   **Template Name:** `maintenance_bill_reminder`
   **Category:** Utility
   **Language:** English

   **Body:**
   ```
   Dear {{1}}, your maintenance bill for {{2}} of Rs.{{3}} is due.
   
   Pay online: {{4}}
   
   - Society Management
   ```

   - `{{1}}` = Owner name
   - `{{2}}` = Month Year (e.g., "August 2026")
   - `{{3}}` = Amount
   - `{{4}}` = Payment link

3. Submit for approval (usually approved within minutes for Utility category)

#### Step 6: Application Integration

Add WhatsApp API configuration to `application-prod.yml`:

```yaml
app:
  whatsapp:
    enabled: true
    api-url: https://graph.facebook.com/v18.0
    phone-number-id: YOUR_PHONE_NUMBER_ID
    access-token: YOUR_PERMANENT_ACCESS_TOKEN
    template-name: maintenance_bill_reminder
    template-language: en
```

Example service code to send template message:

```java
@Service
public class WhatsAppService {

    @Value("${app.whatsapp.api-url}")
    private String apiUrl;

    @Value("${app.whatsapp.phone-number-id}")
    private String phoneNumberId;

    @Value("${app.whatsapp.access-token}")
    private String accessToken;

    @Value("${app.whatsapp.template-name}")
    private String templateName;

    public void sendBillReminder(String ownerPhone, String ownerName, 
                                  String monthYear, String amount, String paymentLink) {
        RestTemplate restTemplate = new RestTemplate();
        String url = apiUrl + "/" + phoneNumberId + "/messages";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
            "messaging_product", "whatsapp",
            "to", "91" + ownerPhone,
            "type", "template",
            "template", Map.of(
                "name", templateName,
                "language", Map.of("code", "en"),
                "components", List.of(
                    Map.of("type", "body", "parameters", List.of(
                        Map.of("type", "text", "text", ownerName),
                        Map.of("type", "text", "text", monthYear),
                        Map.of("type", "text", "text", amount),
                        Map.of("type", "text", "text", paymentLink)
                    ))
                )
            )
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        restTemplate.postForEntity(url, entity, Map.class);
    }
}
```

#### Step 7: Set Up Webhook (Optional - for receiving replies)

1. In Meta Developer Dashboard > WhatsApp > **Configuration**
2. Set Webhook URL: `https://YOUR_DOMAIN/api/whatsapp/webhook`
3. Set Verify Token: a random string you choose
4. Subscribe to: `messages`, `message_deliveries`

### 17.3 WhatsApp Business API Pricing

| Message Type | Cost (India) |
|-------------|--------------|
| Utility (bills, reminders) | ~Rs 0.30 per message |
| Marketing (promotions) | ~Rs 0.75 per message |
| Service (reply within 24h) | Free (first 1000/month) |

> Note: Meta gives 1000 free service conversations per month. Utility/marketing messages are charged per conversation (24-hour window).

### 17.4 Alternative BSPs (Business Solution Providers)

If Meta's direct API seems complex, you can use a BSP which provides easier APIs:

| Provider | Website | Notes |
|----------|---------|-------|
| Twilio | twilio.com | Well-documented, per-message pricing |
| Gupshup | gupshup.io | Indian company, good support |
| Interakt | interakt.shop | Built for Indian businesses |
| Wati | wati.io | Easy dashboard, template management |
| MSG91 | msg91.com | Also provides SMS, email |

These BSPs provide their own REST APIs that are simpler than Meta's direct API. Example with Gupshup:

```yaml
app:
  whatsapp:
    provider: gupshup
    api-url: https://api.gupshup.io/wa/api/v1/msg
    api-key: YOUR_GUPSHUP_API_KEY
    source-number: YOUR_WHATSAPP_NUMBER
```

### 17.5 Testing WhatsApp Integration

1. **Option A (wa.me links):** Click "Share via WhatsApp" on any bill in the UI. Verify WhatsApp opens with correct message.
2. **Option B (Business API):** Use Meta's test phone number in sandbox mode to send messages to your personal WhatsApp.

---

## 18. Complete Production Checklist

Before going live, ensure all of the following are configured:

| Item | Status | Notes |
|------|--------|-------|
| EC2 instance running | [ ] | Ubuntu 22.04, t2.medium |
| MySQL installed and secured | [ ] | Strong passwords, no remote root |
| Backend deployed and running | [ ] | systemd service active |
| Frontend built and served | [ ] | Nginx serving Angular build |
| Nginx configured | [ ] | Reverse proxy + SPA routing |
| SSL/HTTPS enabled | [ ] | Let's Encrypt certificate |
| UFW firewall active | [ ] | Only 22, 80, 443 open |
| Cashfree production keys set | [ ] | KYC approved, keys in config |
| Cashfree webhook configured | [ ] | Points to your domain |
| WhatsApp integration working | [ ] | wa.me links or Business API |
| JWT secret changed | [ ] | Use a strong random string |
| Database backups scheduled | [ ] | Cron job running daily |
| Elastic IP associated | [ ] | Static IP for the instance |
| Domain DNS configured | [ ] | A record pointing to Elastic IP |
| Monitoring set up | [ ] | CloudWatch or htop checks |

---

## 19. Deployment Using Docker

Docker provides a consistent, reproducible deployment. The entire application (MySQL + Backend + Frontend) runs in containers managed by Docker Compose.

### 19.1 Docker Architecture

```
                      Internet
                         |
                    [Port 80]
                         |
              +----------+----------+
              |  Frontend Container  |
              |  (Nginx + Angular)   |
              +----+----------+-----+
                   |          |
           /api/*  |          | /*
                   v          v
          +--------+--+   Static Files
          |  Backend  |   (Angular build)
          |  Container|
          | (Java 17) |
          +--------+--+
                   |
              [Port 3306]
                   |
          +--------+--+
          |   MySQL   |
          | Container |
          +-----------+
              |
        [mysql_data volume]
```

### 19.2 Project Structure (Docker Files)

```
SocietyManagement/
├── docker-compose.yml          # Orchestrates all services
├── .env.example                # Environment variables template
├── .env                        # Your actual env vars (git-ignored)
├── backend/
│   ├── Dockerfile              # Multi-stage: Maven build + JRE runtime
│   └── .dockerignore
└── frontend/
    ├── Dockerfile              # Multi-stage: Node build + Nginx serve
    ├── nginx.conf              # Nginx config for frontend container
    └── .dockerignore
```

### 19.3 Prerequisites

Install Docker and Docker Compose on your EC2 instance:

```bash
# Install Docker
sudo apt update
sudo apt install ca-certificates curl gnupg -y

sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin -y

# Add your user to docker group (avoids sudo for docker commands)
sudo usermod -aG docker $USER
newgrp docker

# Verify
docker --version
docker compose version
```

### 19.4 Quick Start (Development)

```bash
# Clone the repo
git clone https://github.com/YOUR_USERNAME/SocietyManagement.git
cd SocietyManagement

# Create .env file from template
cp .env.example .env
# Edit .env with your actual values
nano .env

# Build and start all containers
docker compose up -d --build

# Check status
docker compose ps

# View logs
docker compose logs -f
```

The application will be available at `http://localhost` (or `http://<YOUR_EC2_IP>`).

### 19.5 Environment Variables

Create a `.env` file in the project root (copy from `.env.example`):

```env
# MySQL
MYSQL_ROOT_PASSWORD=StrongRootPassword@123
MYSQL_PASSWORD=SocietyApp@123

# JWT
JWT_SECRET=ChangeThisToASecureRandomStringAtLeast64CharsLong_abc123xyz789

# Cashfree Payment Gateway
CASHFREE_APP_ID=your_cashfree_app_id
CASHFREE_SECRET_KEY=your_cashfree_secret_key
CASHFREE_ENVIRONMENT=production
CASHFREE_RETURN_URL=https://yourdomain.com/maintenance/payment-status
CASHFREE_NOTIFY_URL=https://yourdomain.com/api/maintenance/payments/webhook

# Application
APP_BASE_URL=https://yourdomain.com/api
```

> Never commit the `.env` file to git. It's already in `.gitignore`.

### 19.6 Docker Compose Services

| Service | Container Name | Image | Port | Description |
|---------|---------------|-------|------|-------------|
| mysql | society-mysql | mysql:8.0 | 3306 | Database with persistent volume |
| backend | society-backend | Custom (Spring Boot) | 8080 | REST API server |
| frontend | society-frontend | Custom (Nginx + Angular) | 80 | Static frontend + reverse proxy |

### 19.7 Dockerfile Details

#### Backend Dockerfile (Multi-stage)

```dockerfile
# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Run with slim JRE
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd -r appuser && useradd -r -g appuser appuser
RUN mkdir -p /app/uploads /app/logs && chown -R appuser:appuser /app
COPY --from=build /app/target/society-management-1.0.0-SNAPSHOT.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "-Xms512m", "-Xmx1024m", "app.jar"]
```

#### Frontend Dockerfile (Multi-stage)

```dockerfile
# Stage 1: Build Angular app
FROM node:18-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npx ng build --configuration production

# Stage 2: Serve with Nginx
FROM nginx:1.25-alpine
COPY --from=build /app/dist/society-management-frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### 19.8 Production Deployment on EC2

#### Step 1: SSH into your EC2 instance

```bash
ssh -i your-key.pem ubuntu@<YOUR_ELASTIC_IP>
```

#### Step 2: Install Docker (see section 19.3)

#### Step 3: Clone and configure

```bash
cd /opt
sudo mkdir society-management && sudo chown ubuntu:ubuntu society-management
cd society-management
git clone https://github.com/YOUR_USERNAME/SocietyManagement.git .
cp .env.example .env
nano .env   # Set production values
```

#### Step 4: Build and start

```bash
docker compose up -d --build
```

First build takes 5-10 minutes (downloads dependencies). Subsequent builds use cached layers.

#### Step 5: Verify all services are running

```bash
docker compose ps
```

Expected output:
```
NAME                STATUS              PORTS
society-backend     Up (healthy)        0.0.0.0:8080->8080/tcp
society-frontend    Up                  0.0.0.0:80->80/tcp
society-mysql       Up (healthy)        0.0.0.0:3306->3306/tcp
```

#### Step 6: Test the application

```bash
# Test backend API
curl http://localhost:8080/api/api-docs

# Test frontend
curl -I http://localhost
```

### 19.9 SSL/HTTPS with Docker

For HTTPS, add a Certbot container or use a reverse proxy. Simplest approach with Certbot:

```bash
# Install certbot on the host (not in container)
sudo apt install certbot -y

# Stop frontend temporarily to free port 80
docker compose stop frontend

# Get certificate
sudo certbot certonly --standalone -d yourdomain.com

# Restart frontend
docker compose start frontend
```

Then update `frontend/nginx.conf` to include SSL:

```nginx
server {
    listen 80;
    server_name yourdomain.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl;
    server_name yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/yourdomain.com/privkey.pem;

    root /usr/share/nginx/html;
    index index.html;

    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 10M;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

Add SSL volume mount in `docker-compose.yml` under frontend:

```yaml
frontend:
  volumes:
    - /etc/letsencrypt:/etc/letsencrypt:ro
  ports:
    - "80:80"
    - "443:443"
```

### 19.10 Common Docker Commands

```bash
# Start all services
docker compose up -d

# Stop all services
docker compose down

# Stop and remove volumes (DESTROYS DATA)
docker compose down -v

# Rebuild a specific service
docker compose build backend
docker compose up -d backend

# View logs for a specific service
docker compose logs -f backend
docker compose logs -f mysql
docker compose logs -f frontend

# Enter a running container
docker exec -it society-backend sh
docker exec -it society-mysql mysql -u root -p

# Check resource usage
docker stats

# Restart a single service
docker compose restart backend
```

### 19.11 Updating the Application

#### Update Backend Only

```bash
git pull origin main
docker compose build backend
docker compose up -d backend
```

#### Update Frontend Only

```bash
git pull origin main
docker compose build frontend
docker compose up -d frontend
```

#### Update Everything

```bash
git pull origin main
docker compose up -d --build
```

### 19.12 Database Backup with Docker

```bash
# Manual backup
docker exec society-mysql mysqldump -u root -p${MYSQL_ROOT_PASSWORD} society_management > backup_$(date +%Y%m%d).sql

# Restore from backup
docker exec -i society-mysql mysql -u root -p${MYSQL_ROOT_PASSWORD} society_management < backup_20260812.sql
```

Automated backup script:

```bash
#!/bin/bash
# /opt/society-management/docker-backup.sh
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/opt/society-management/backups"
mkdir -p $BACKUP_DIR

# Load env vars
source /opt/society-management/.env

docker exec society-mysql mysqldump -u root -p${MYSQL_ROOT_PASSWORD} society_management > $BACKUP_DIR/society_mgmt_$TIMESTAMP.sql

# Compress
gzip $BACKUP_DIR/society_mgmt_$TIMESTAMP.sql

# Keep only last 7 days
find $BACKUP_DIR -name "*.sql.gz" -mtime +7 -delete

echo "Backup completed: society_mgmt_$TIMESTAMP.sql.gz"
```

Add to cron:

```bash
chmod +x /opt/society-management/docker-backup.sh
crontab -e
# Add: 0 2 * * * /opt/society-management/docker-backup.sh
```

### 19.13 Docker vs Manual Deployment - Comparison

| Aspect | Manual (Sections 1-15) | Docker (Section 19) |
|--------|------------------------|---------------------|
| Setup time | 30-60 minutes | 10-15 minutes |
| Reproducibility | Manual steps, error-prone | Identical every time |
| Isolation | Services share host OS | Each service isolated |
| Updates | Build on server, restart service | Rebuild image, restart container |
| Rollback | Manual JAR swap | `docker compose up -d` with old image |
| Resource overhead | Lower (no container layer) | Slightly higher (~100MB overhead) |
| Debugging | Direct access to processes | Need `docker exec` to enter container |
| Best for | Simple single-server setups | Consistent deployments, CI/CD pipelines |

### 19.14 Troubleshooting Docker

| Problem | Solution |
|---------|----------|
| `docker compose up` fails | Check `.env` file exists with correct values |
| Backend can't connect to MySQL | Ensure MySQL health check passes: `docker compose logs mysql` |
| Frontend shows 502 on /api | Backend hasn't started yet. Wait or check: `docker compose logs backend` |
| Out of disk space | Clean unused images: `docker system prune -a` |
| Container keeps restarting | Check logs: `docker compose logs <service>` |
| Build takes too long | Ensure `.dockerignore` excludes `node_modules/` and `target/` |
| Port 80 already in use | Stop host Nginx: `sudo systemctl stop nginx` |
| Permission denied on volumes | Check volume ownership, run `docker compose down -v` and restart |
