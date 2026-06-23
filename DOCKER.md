# Docker Deployment Guide

## Quick Start

### 1. Prepare Environment Variables
```bash
cp .env.example .env
# Edit .env with your actual API keys and MySQL credentials
nano .env  # or use your favorite editor
```

### 2. Build and Run with Docker Compose
```bash
# Build the image (only needed once or when Dockerfile changes)
docker-compose build

# Run the application with MySQL
docker-compose up -d

# View logs
docker-compose logs -f

# Stop the application
docker-compose down
```

The application will be available at `http://localhost:8080`
MySQL will be available at `localhost:3306`

---

## Detailed Setup Instructions

### Prerequisites
- Docker (v20.10+)
- Docker Compose (v1.29+)
- Git

### Step 1: Clone and Setup

```bash
git clone <your-repo-url>
cd magic-tale-service
cp .env.example .env
```

### Step 2: Configure Environment Variables

Edit `.env` file with your configuration:

```bash
# MySQL Configuration
DB_HOST=mysql
DB_PORT=3306
DB_NAME=storybook_db
DB_USER=storybook
DB_PASSWORD=your_secure_password

# Required API Keys
GEMINI_API_KEY=your_key_here
OPENAI_API_KEY=your_key_here
ANTHROPIC_API_KEY=your_key_here
LEONARDO_API_KEY=your_key_here

# OAuth2 (for Google login)
GOOGLE_CLIENT_ID=your_id_here
GOOGLE_CLIENT_SECRET=your_secret_here

# JWT Secret (must be at least 32 characters)
JWT_SECRET=YourVerySecureSecretKeyOf32PlusChars!

# Frontend URL (where your React app is hosted)
FRONTEND_URL=http://localhost:4200
```

### Step 3: Build Docker Image

```bash
docker-compose build
```

### Step 4: Run the Application

```bash
# Start in background
docker-compose up -d

# View logs
docker-compose logs -f magic-tale-service

# Check health status
curl http://localhost:8080/actuator/health

# Check MySQL connection
docker exec magic-tale-mysql mysql -u storybook -p -e "SELECT 1;"
```

### Step 5: Stop the Application

```bash
docker-compose down

# Keep database volume (data persists)
docker-compose down  

# Remove database (clean slate)
docker-compose down -v
```

---

## Database Management

### Connect to MySQL

```bash
# From host machine
mysql -h localhost -P 3306 -u storybook -p

# From inside container
docker exec -it magic-tale-mysql mysql -u storybook -p storybook_db
```

### Backup Database

```bash
# Dump entire database
docker exec magic-tale-mysql mysqldump -u storybook -p storybook_db > backup.sql

# Without password prompt
docker exec magic-tale-mysql mysqldump -u storybook -pstorybook123 storybook_db > backup.sql
```

### Restore Database

```bash
docker exec -i magic-tale-mysql mysql -u storybook -pstorybook123 storybook_db < backup.sql
```

### View Database Logs

```bash
docker logs magic-tale-mysql
```

---

## Manual Docker Commands (without Docker Compose)

### Step 1: Start MySQL Container
```bash
docker run -d \
  --name magic-tale-mysql \
  -e MYSQL_ROOT_PASSWORD=rootpassword \
  -e MYSQL_DATABASE=storybook_db \
  -e MYSQL_USER=storybook \
  -e MYSQL_PASSWORD=storybook123 \
  -p 3306:3306 \
  -v mysql-data:/var/lib/mysql \
  mysql:8.0
```

### Step 2: Build Application Image
```bash
docker build -t magic-tale-service:latest .
```

### Step 3: Run Application Container
```bash
docker run -d \
  --name magic-tale-service \
  --link magic-tale-mysql:mysql \
  -p 8080:8080 \
  -e DB_HOST=magic-tale-mysql \
  -e DB_PORT=3306 \
  -e DB_NAME=storybook_db \
  -e DB_USER=storybook \
  -e DB_PASSWORD=storybook123 \
  -e CHAT_MODEL=gemini \
  -e GEMINI_API_KEY=$GEMINI_API_KEY \
  -e OPENAI_API_KEY=$OPENAI_API_KEY \
  -e ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY \
  -e LEONARDO_API_KEY=$LEONARDO_API_KEY \
  -e GOOGLE_CLIENT_ID=$GOOGLE_CLIENT_ID \
  -e GOOGLE_CLIENT_SECRET=$GOOGLE_CLIENT_SECRET \
  -e JWT_SECRET="YourSecretKey" \
  -e FRONTEND_URL=http://localhost:4200 \
  magic-tale-service:latest
```

### View Logs

```bash
docker logs -f magic-tale-service
docker logs -f magic-tale-mysql
```

### Stop Containers

```bash
docker stop magic-tale-service magic-tale-mysql
docker rm magic-tale-service magic-tale-mysql
```

---

## Production Deployment

### Using Docker Registry (Docker Hub)

1. **Tag your image**
   ```bash
   docker tag magic-tale-service:latest yourusername/magic-tale-service:1.0.0
   ```

2. **Push to registry**
   ```bash
   docker login
   docker push yourusername/magic-tale-service:1.0.0
   ```

3. **Pull and run on production server**
   ```bash
   docker pull yourusername/magic-tale-service:1.0.0
   docker-compose up -d
   ```

### Using Kubernetes (Optional)

For Kubernetes deployment, see `k8s/` directory for manifests (if available).

---

## Performance Tuning

### MySQL Performance

```yaml
# In docker-compose.yml
mysql:
  command: --max_connections=1000 --default-storage-engine=InnoDB
```

### Application Memory

```yaml
# In docker-compose.yml
magic-tale-service:
  deploy:
    resources:
      limits:
        memory: 2G
      reservations:
        memory: 1G
```

---

## Health Checks

The application includes a built-in health check:

```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{"status":"UP"}
```

MySQL health check:
```bash
docker exec magic-tale-mysql mysqladmin ping -h localhost
```

---

## Troubleshooting

### Container won't start
```bash
docker-compose logs magic-tale-service
docker-compose logs mysql
```

### MySQL connection failed
```bash
# Wait for MySQL to be ready
docker-compose up -d mysql
sleep 10
docker-compose up -d magic-tale-service
```

### Database permission errors
```bash
# Verify MySQL is running
docker exec magic-tale-mysql mysql -u storybook -pstorybook123 -e "SHOW DATABASES;"
```

### Port already in use
```bash
# Use different ports in docker-compose.yml
mysql:
  ports:
    - "3307:3306"  # Changed from 3306
magic-tale-service:
  ports:
    - "8081:8080"  # Changed from 8080
```

### Clean database (fresh start)
```bash
docker-compose down -v
docker-compose up -d
```

---

## Environment Variables Reference

### Database
- `DB_HOST`: MySQL host (default: mysql)
- `DB_PORT`: MySQL port (default: 3306)
- `DB_NAME`: Database name (default: storybook_db)
- `DB_USER`: MySQL user (default: storybook)
- `DB_PASSWORD`: MySQL password

### Application
- `PORT`: Application port (default: 8080)
- `CHAT_MODEL`: `gemini` | `openai` | `anthropic`
- `IMAGE_MODEL`: `leonardo` | `openai` | `none`
- `LOG_LEVEL`: `INFO` | `DEBUG` | `WARN` | `ERROR`

### API Keys
- `GEMINI_API_KEY`: Google Gemini API key
- `OPENAI_API_KEY`: OpenAI API key
- `ANTHROPIC_API_KEY`: Anthropic API key
- `LEONARDO_API_KEY`: Leonardo AI API key

### Security
- `GOOGLE_CLIENT_ID`: OAuth2 client ID
- `GOOGLE_CLIENT_SECRET`: OAuth2 client secret
- `JWT_SECRET`: JWT signing secret (32+ chars)
- `JWT_EXPIRATION_MS`: JWT expiration (milliseconds)

### Other
- `FRONTEND_URL`: Frontend URL for redirects
- `ADMIN_AUDIT_PASSWORD`: Admin audit endpoint password
- `GENERATE_PDF_IMAGE_ENABLE`: Enable PDF image generation

---

## Support & Documentation

- Spring Boot: https://spring.io/projects/spring-boot
- Spring AI: https://spring.io/projects/spring-ai
- MySQL: https://dev.mysql.com/doc/
- Docker: https://docs.docker.com/
- Docker Compose: https://docs.docker.com/compose/
