# 国内云厂商部署指南

本文档介绍如何将「数据分析 AI Agent」（Spring Boot + React）部署到国内云服务器。

---

## 一、云厂商选择

| 厂商 | 入门规格 | 年费 | 带宽 | 推荐场景 |
|------|----------|------|------|----------|
| **阿里云 轻量应用服务器** | 2 核 2G | ~99 元 | 3Mbps | 通用首选，镜像生态完善 |
| **腾讯云 轻量应用服务器** | 2 核 2G | ~99 元 | 3Mbps | 与阿里云对标 |
| **华为云 云耀云服务器** | 2 核 2G | ~88 元 | 3Mbps | 性价比稍高 |
| **京东云 轻量云主机** | 2 核 2G | ~48 元 | 2Mbps | 最便宜 |

> **Tips**：新用户首年都有大额优惠，上面写的都是新用户价。如果不是新用户可以关注双 11 / 618 活动。

### 推荐配置

- **系统镜像**：Ubuntu 22.04 LTS
- **最低规格**：2 核 2G（1 核 1G 跑 Spring Boot + DuckDB 不太够）
- **系统盘**：40GB 起
- **带宽**：3Mbps 够日常使用

---

## 二、环境准备

### 2.1 购买服务器后 SSH 登录

```bash
ssh root@<你的服务器公网IP>
```

### 2.2 基础配置

```bash
# 更新系统
apt update && apt upgrade -y

# 设置时区
timedatectl set-timezone Asia/Shanghai

# 创建应用用户（不要用 root 跑应用）
useradd -m -s /bin/bash app
usermod -aG sudo app
```

### 2.3 安装 JDK 17

```bash
apt install openjdk-17-jdk -y
java -version
# 输出：openjdk version "17.0.x" ...
```

### 2.4 安装 Maven（构建用，也可本地构建后上传 jar）

```bash
# 方式一：apt 安装（版本可能较旧，但够用）
apt install maven -y

# 方式二：手动安装最新版（推荐）
cd /tmp
wget https://dlcdn.apache.org/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz
tar xzf apache-maven-3.9.9-bin.tar.gz -C /opt
ln -s /opt/apache-maven-3.9.9 /opt/maven

cat >> /etc/profile.d/maven.sh << 'EOF'
export M2_HOME=/opt/maven
export PATH=$M2_HOME/bin:$PATH
EOF
source /etc/profile.d/maven.sh
mvn -version
```

### 2.5 安装 Node.js（构建前端用）

```bash
# 使用 NodeSource 官方源安装 Node 20 LTS
curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
apt install nodejs -y
node -v   # v20.x.x
npm -v    # 10.x.x
```

---

## 三、构建项目

### 3.1 上传代码到服务器

```bash
# 在本地执行，把代码传到服务器
scp -r /path/to/java-duckdb-demo app@<服务器IP>:/home/app/

# 或者在服务器上 git clone
# git clone <你的仓库地址> /home/app/java-duckdb-demo
```

### 3.2 一键构建（前端 + 后端打包）

```bash
cd /home/app/java-duckdb-demo

# 复制并填写环境变量
cp .env.example .env
vim .env
```

编辑 `.env`：

```bash
# === LLM 配置（必须修改）===
# 用哪个大模型就填哪个
AI_BASE_URL=https://api.deepseek.com
AI_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
AI_MODEL=deepseek-chat

# === JWT 密钥（必须修改，保持唯一）===
JWT_SECRET=$(openssl rand -base64 32 | tr -d '\n')
# 或手动写一个长随机字符串

# === DuckDB 数据目录 ===
APP_DUCKDB_STORAGE_PATH=/home/app/data/duckdb
APP_UPLOAD_STORAGE_PATH=/home/app/data/uploads

# === 数据库 ===
SPRING_DATASOURCE_URL=jdbc:h2:file:/home/app/data/agentdb;DB_CLOSE_ON_EXIT=FALSE

# === 服务端口 ===
SERVER_PORT=8080
```

构建：

```bash
# 安装前端依赖并构建
cd frontend && npm install && npm run build && cd ..

# 复制前端产物到后端静态资源目录
mkdir -p src/main/resources/static
cp -r frontend/dist/* src/main/resources/static/

# Maven 打包，跳过测试
mvn clean package -DskipTests -q
```

构建成功后，jar 包在 `target/data-analysis-agent-1.0.0.jar`。

---

## 四、部署运行

### 4.1 创建数据目录

```bash
mkdir -p /home/app/data/duckdb
mkdir -p /home/app/data/uploads
mkdir -p /home/app/logs
chown -R app:app /home/app
```

### 4.2 配置 systemd 服务（开机自启 + 进程守护）

```bash
cat > /etc/systemd/system/data-analysis-agent.service << 'EOF'
[Unit]
Description=Data Analysis AI Agent
After=network.target

[Service]
Type=simple
User=app
Group=app
WorkingDirectory=/home/app/java-duckdb-demo

# 环境变量（从 .env 加载）
EnvironmentFile=/home/app/java-duckdb-demo/.env

# 启动命令
ExecStart=/usr/bin/java \
  -Xms512m -Xmx1024m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -Djava.awt.headless=true \
  -Dserver.port=8080 \
  -jar /home/app/java-duckdb-demo/target/data-analysis-agent-1.0.0.jar

# 自动重启策略
Restart=on-failure
RestartSec=10

# 日志
StandardOutput=append:/home/app/logs/stdout.log
StandardError=append:/home/app/logs/stderr.log

# 安全加固（可选）
NoNewPrivileges=yes
ProtectSystem=strict
ProtectHome=yes
ReadWritePaths=/home/app/data /home/app/logs

[Install]
WantedBy=multi-user.target
EOF
```

启动服务：

```bash
systemctl daemon-reload
systemctl enable data-analysis-agent
systemctl start data-analysis-agent

# 检查状态
systemctl status data-analysis-agent

# 查看日志
journalctl -u data-analysis-agent -f
```

### 4.3 验证

```bash
# 本地验证
curl http://localhost:8080/api/models
# 应返回 JSON 模型列表

# 注册账号
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"your-password"}'
```

---

## 五、Nginx 反向代理

### 5.1 安装 Nginx

```bash
apt install nginx -y
```

### 5.2 配置站点

```bash
cat > /etc/nginx/sites-available/data-analysis << 'NGINX'
server {
    listen 80;
    server_name your-domain.com;  # 改成你的域名

    # 最大上传 50MB
    client_max_body_size 50m;

    # 日志
    access_log /var/log/nginx/data-analysis-access.log;
    error_log  /var/log/nginx/data-analysis-error.log;

    # API + 前端（Spring Boot 内置静态资源）
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # SSE 流式响应需要关闭缓冲
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;
    }

    # WebSocket（如果有）
    location /ws/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 86400;
    }
}
NGINX
```

### 5.3 启用站点

```bash
ln -sf /etc/nginx/sites-available/data-analysis /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default
nginx -t          # 测试配置
systemctl reload nginx
```

---

## 六、HTTPS（Let's Encrypt 免费证书）

### 6.1 域名准备

在云厂商控制台添加 **A 记录**，将你的域名指向服务器 IP：

```
类型: A
主机记录: @（或子域名如 api）
记录值: <服务器公网IP>
TTL: 600
```

### 6.2 申请证书

```bash
# 安装 certbot
apt install certbot python3-certbot-nginx -y

# 自动配置（交互式，选择域名填写邮箱即可）
certbot --nginx -d your-domain.com

# 检查自动续期
certbot renew --dry-run
```

完成后 Nginx 配置会自动加上 HTTPS。访问 `https://your-domain.com` 即可。

---

## 七、前端分部署方案（可选）

如果想把前端和 API 分开部署：

### 前端 → 腾讯云 COS / 阿里云 OSS 静态托管

| 厂商 | 产品 | 免费额度 | 月费（小流量） |
|------|------|----------|----------------|
| 阿里云 | OSS 静态网站 | 5GB 存储 + 5GB 流量 | ~0 元 |
| 腾讯云 | COS 静态网站 | 10GB 存储 + 10GB 流量 | ~0 元 |

```bash
# 以阿里云 OSS 为例，安装 ossutil
# 构建前端后上传
cd frontend && npm run build
ossutil cp -rf dist/ oss://your-bucket/ --acl public-read

# 然后 Nginx 配置改为：
# location /api/ {
#     proxy_pass http://127.0.0.1:8080;
# }
# 前端访问 OSS 域名 / 自定义域名
```

---

## 八、安全加固

```bash
# 1. 防火墙：只开放必要端口
ufw allow 22/tcp    # SSH
ufw allow 80/tcp    # HTTP
ufw allow 443/tcp   # HTTPS
ufw enable

# 2. 禁止 root 远程登录
sed -i 's/#PermitRootLogin prohibit-password/PermitRootLogin no/' /etc/ssh/sshd_config
systemctl restart sshd

# 3. 云厂商控制台 -> 安全组
# 同样只放行 22 / 80 / 443

# 4. fail2ban 防暴力破解
apt install fail2ban -y
systemctl enable fail2ban
```

---

## 九、日常运维

### 常用命令

```bash
# 查看应用状态
systemctl status data-analysis-agent

# 重启应用（代码更新后）
systemctl restart data-analysis-agent

# 查看实时日志
journalctl -u data-analysis-agent -f --since "5 min ago"

# 磁盘使用
df -h
du -sh /home/app/data/duckdb/

# 内存使用
free -h
ps aux --sort=-%mem | head -5
```

### 备份数据

```bash
# DuckDB 数据文件直接复制即可（单文件数据库）
tar czf /home/app/backups/duckdb-$(date +%Y%m%d).tar.gz \
  /home/app/data/duckdb/ \
  /home/app/data/agentdb.mv.db \
  /home/app/data/uploads/

# 添加到 crontab，每天凌晨 2 点备份
# crontab -e
# 0 2 * * * tar czf /home/app/backups/app-$(date +\%Y\%m\%d).tar.gz /home/app/data/
```

### 更新部署

```bash
# 1. 停止服务
systemctl stop data-analysis-agent

# 2. 拉取新代码 / 上传新 jar
cd /home/app/java-duckdb-demo
git pull

# 3. 重建
cd frontend && npm install && npm run build && cd ..
rm -rf src/main/resources/static/*
cp -r frontend/dist/* src/main/resources/static/
mvn clean package -DskipTests -q

# 4. 启动
systemctl start data-analysis-agent
```

### 监控告警

```bash
# 简单内存告警脚本
cat > /home/app/scripts/check-memory.sh << 'EOF'
#!/bin/bash
MEM=$(free | awk '/Mem/{printf("%.0f"), $3/$2*100}')
if [ "$MEM" -gt 85 ]; then
    echo "Memory usage: ${MEM}%" | mail -s "Server Alert" your-email@qq.com
fi
EOF
chmod +x /home/app/scripts/check-memory.sh

# crontab -e
# */30 * * * * /home/app/scripts/check-memory.sh
```

---

## 十、多模型切换

部署后，在 `.env` 文件中切换 LLM 提供商：

```bash
# DeepSeek（推荐，便宜好用）
AI_BASE_URL=https://api.deepseek.com
AI_API_KEY=sk-xxx
AI_MODEL=deepseek-chat

# Kimi / Moonshot
AI_BASE_URL=https://api.moonshot.cn/v1
AI_API_KEY=sk-xxx
AI_MODEL=moonshot-v1-8k

# MiniMax
AI_BASE_URL=https://api.minimax.chat/v1
AI_API_KEY=xxx
AI_MODEL=abab6.5s-chat

# 修改后重启
systemctl restart data-analysis-agent
```

---

## 附录：完整命令汇总（新服务器一键初始化）

把以下内容保存为 `init-server.sh`，SSH 到新服务器后直接执行：

```bash
#!/bin/bash
set -e

echo "=== 更新系统 ==="
apt update && apt upgrade -y
timedatectl set-timezone Asia/Shanghai

echo "=== 安装依赖 ==="
apt install -y openjdk-17-jdk maven nginx certbot python3-certbot-nginx fail2ban ufw

# Node.js 20
curl -fsSL https://deb.nodesource.com/setup_20.x | bash -
apt install -y nodejs

echo "=== 创建用户和目录 ==="
useradd -m -s /bin/bash app 2>/dev/null || true
usermod -aG sudo app
mkdir -p /home/app/data/{duckdb,uploads} /home/app/logs /home/app/backups
chown -R app:app /home/app

echo "=== 防火墙 ==="
ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp
ufw --force enable

echo "=== 基础安全 ==="
sed -i 's/#PermitRootLogin prohibit-password/PermitRootLogin no/' /etc/ssh/sshd_config
systemctl restart sshd
systemctl enable fail2ban --now

echo ""
echo "✅ 初始化完成！接下来："
echo "1. 上传项目代码到 /home/app/java-duckdb-demo"
echo "2. 配置 .env 文件（特别是 AI_API_KEY）"
echo "3. 运行构建：cd /home/app/java-duckdb-demo && ./run.sh"
echo "4. 配置 systemd 服务和 Nginx"
```
