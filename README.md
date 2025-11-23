# DHT Spider - DHT爬虫系统

基于Java的分布式DHT爬虫系统，用于发现、下载和存储BT种子元数据。

## 项目结构

```
dht-spider-java/
├── dht-common/              # 通用模块：共享实体类、常量、工具类
├── dht-mldht/               # MLDHT爬虫服务：监听DHT网络，发现InfoHash
├── dht-bt-client/           # BT客户端服务：下载种子元数据
├── dht-metadata-service/    # 元数据服务：存储和查询API（Spring Boot）
└── pom.xml                  # 父POM
```

## 系统架构

```
┌─────────────┐        ┌──────────┐        ┌─────────────┐        ┌──────────────┐
│  DHT Network│───────>│  MLDHT   │───────>│  RedPanda   │───────>│  BT Client   │
└─────────────┘        │  Service │        │  (Kafka)    │        │  Service     │
                       └──────────┘        └─────────────┘        └──────────────┘
                            │                                             │
                            │              ┌──────────┐                  │
                            └─────────────>│  Redis   │<─────────────────┘
                                           │ (去重)   │
                                           └──────────┘
                                                                          │
                                                                          ▼
                                           ┌─────────────┐        ┌──────────────┐
                                           │  RedPanda   │───────>│  Metadata    │
                                           │  (Kafka)    │        │  Service     │
                                           └─────────────┘        └──────────────┘
                                                                          │
                                                                          ▼
                                                                   ┌──────────────┐
                                                                   │  PostgreSQL  │
                                                                   └──────────────┘
```

## 模块说明

### 1. dht-common
通用模块，包含：
- **实体类**：`InfoHashMessage`、`TorrentMetadata`
- **常量**：Kafka主题、Redis键
- **工具类**：Hash工具、编解码工具

### 2. dht-mldht
MLDHT爬虫服务（Spring Boot应用，无Web）：
- 监听DHT网络消息
- 发现新的InfoHash
- 通过Redis去重
- 将InfoHash发布到RedPanda主题

### 3. dht-bt-client
BT客户端服务（Spring Boot应用，无Web）：
- 从RedPanda消费InfoHash
- 通过Peer协议下载元数据
- 将下载的元数据发布到RedPanda
- 失败重试和记录

### 4. dht-metadata-service
元数据服务（Spring Boot Web应用）：
- 从RedPanda消费元数据
- 存储到PostgreSQL数据库
- 提供REST API查询接口

## 中间件

- **RedPanda**（Kafka兼容）：模块间消息传递
- **Redis**：全局去重和缓存
- **PostgreSQL**：元数据持久化存储

## 快速开始

### 前置要求
- Docker & Docker Compose（推荐）
- 或 Java 21+ & Maven 3.6+（本地开发）

### 方式一：使用 Docker Compose（推荐）

#### 一键启动所有服务

**Linux/Mac:**
```bash
chmod +x start.sh
./start.sh
```

**Windows (PowerShell):**
```powershell
.\start.ps1
```

**或手动启动:**
```bash
docker-compose up -d --build
```

**使用 Makefile（如果已安装 make）:**
```bash
# 查看所有可用命令
make help

# 构建并启动
make all

# 查看日志
make logs
make logs-mldht
make logs-btclient
make logs-metadata

# 查看统计
make stats
```

#### 查看服务状态
```bash
docker-compose ps
```

#### 查看日志
```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f dht-mldht
docker-compose logs -f dht-bt-client
docker-compose logs -f dht-metadata-service
```

#### 停止服务
```bash
docker-compose stop
```

#### 完全删除（包括数据卷）
```bash
docker-compose down -v
```

### 方式二：本地开发模式

#### 1. 启动中间件

```bash
docker-compose up -d redpanda redis postgres console
```

#### 2. 编译项目

```bash
mvn clean package
```

#### 3. 启动服务

**启动MLDHT服务：**
```bash
java -jar dht-mldht/target/dht-mldht-1.0.0-SNAPSHOT.jar
```

**启动BT客户端服务：**
```bash
java -jar dht-bt-client/target/dht-bt-client-1.0.0-SNAPSHOT.jar
```

**启动元数据服务：**
```bash
java -jar dht-metadata-service/target/dht-metadata-service-1.0.0-SNAPSHOT.jar
```

## 服务访问地址

启动后可访问以下服务：

- **元数据 REST API**: http://localhost:8080
- **RedPanda Console**: http://localhost:8081 （Kafka 管理界面）
- **Kafka**: localhost:9092
- **Redis**: localhost:6380
- **PostgreSQL**: localhost:5433

## 端口说明

| 服务 | 端口范围 | 协议 | 说明 |
|-----|---------|------|------|
| dht-mldht | 6881-6900 | UDP | DHT 网络节点监听端口 |
| dht-bt-client | 6901-6950 | TCP/UDP | BT 客户端 Peer 连接端口 |
| dht-metadata-service | 8080 | HTTP | REST API 端口 |
| RedPanda (Kafka) | 9092 | TCP | Kafka 协议端口 |
| RedPanda Console | 8081 | HTTP | Web 管理界面 |
| Redis | 6380 | TCP | Redis 协议端口 |
| PostgreSQL | 5433 | TCP | PostgreSQL 数据库端口 |

> ⚠️ **注意**：DHT 和 BT 端口需要对外开放（防火墙/路由器端口转发），否则会影响发现和下载效率

## API 接口

### 健康检查
```bash
curl http://localhost:8080/api/v1/torrents/health
```

### 统计信息
```bash
curl http://localhost:8080/api/v1/torrents/stats
```

### 根据 InfoHash 查询
```bash
curl http://localhost:8080/api/v1/torrents/{infoHash}
```

### 搜索种子
```bash
curl "http://localhost:8080/api/v1/torrents/search?keyword=电影&page=0&size=20"
```

### 获取最新种子
```bash
curl "http://localhost:8080/api/v1/torrents/latest?page=0&size=20"
```

## 配置说明

各模块的配置文件位于 `src/main/resources/application.properties`

关键配置项：
- Kafka/RedPanda地址
- Redis地址
- PostgreSQL数据库连接
- 线程池大小
- 超时设置
- Spring Boot配置（web-application-type=none for MLDHT & BT Client）

## 开发计划

- [x] 创建项目骨架
- [x] 实现MLDHT协议监听（基于 atomashpolskiy/bt）
- [x] 实现BT元数据下载（BEP-9 协议）
- [x] 实现客户端池化机制（默认10个客户端）
- [x] 实现元数据存储和查询
- [x] 添加监控和统计
- [ ] 性能优化和调优
- [ ] 添加 Web 管理界面

## 故障排查

### 服务启动失败

1. **检查 Docker 资源**
   ```bash
   docker stats
   ```
   确保有足够的 CPU 和内存（建议至少 4GB RAM）

2. **查看服务日志**
   ```bash
   docker-compose logs [service-name]
   ```

3. **检查端口占用**
   ```bash
   # Windows
   netstat -ano | findstr ":8080"
   netstat -ano | findstr ":9092"
   
   # Linux/Mac
   lsof -i :8080
   lsof -i :9092
   ```

### 常见问题

**Q: Kafka 连接失败？**
- 等待 RedPanda 完全启动（约 30 秒）
- 检查防火墙设置
- 查看 RedPanda 日志：`docker-compose logs redpanda`

**Q: 数据库连接失败？**
- 确认 PostgreSQL 已启动：`docker-compose ps postgres`
- 检查数据库凭据是否正确
- 查看数据库日志：`docker-compose logs postgres`

**Q: Redis 连接失败？**
- 确认 Redis 已启动：`docker-compose ps redis`
- 端口是否被占用：6380

**Q: MLDHT 没有发现 InfoHash？**
- DHT 网络发现需要时间（可能需要几分钟到几小时）
- 检查网络连接
- 查看日志确认是否有错误

### 网络配置

#### 防火墙端口开放

**Windows 防火墙:**
```powershell
# DHT 端口
New-NetFirewallRule -DisplayName "DHT-MLDHT" -Direction Inbound -Protocol UDP -LocalPort 6881-6900 -Action Allow

# BT 客户端端口
New-NetFirewallRule -DisplayName "BT-Client-TCP" -Direction Inbound -Protocol TCP -LocalPort 6901-6950 -Action Allow
New-NetFirewallRule -DisplayName "BT-Client-UDP" -Direction Inbound -Protocol UDP -LocalPort 6901-6950 -Action Allow
```

**Linux 防火墙 (ufw):**
```bash
# DHT 端口
sudo ufw allow 6881:6900/udp

# BT 客户端端口
sudo ufw allow 6901:6950/tcp
sudo ufw allow 6901:6950/udp
```

**Linux 防火墙 (firewalld):**
```bash
# DHT 端口
sudo firewall-cmd --permanent --add-port=6881-6900/udp

# BT 客户端端口
sudo firewall-cmd --permanent --add-port=6901-6950/tcp
sudo firewall-cmd --permanent --add-port=6901-6950/udp

# 重载配置
sudo firewall-cmd --reload
```

#### 路由器端口转发

如果部署在内网，需要在路由器上配置端口转发：
- **DHT**: 转发 UDP 6881-6900 到服务器 IP
- **BT Client**: 转发 TCP/UDP 6901-6950 到服务器 IP

### 性能调优

1. **调整 BT 客户端池大小**
   编辑 `dht-bt-client/src/main/resources/application.properties`：
   ```properties
   bt.client.pool-size=20  # 默认 10
   ```

2. **调整 Kafka 并发度**
   编辑各服务的配置文件，修改 `concurrency` 参数

3. **增加数据库连接池**
   编辑 `dht-metadata-service/src/main/resources/application.properties`：
   ```properties
   spring.datasource.hikari.maximum-pool-size=20
   ```

## 监控

### RedPanda Console
访问 http://localhost:8081 查看：
- Kafka 主题和消息
- 消费者组状态
- 消息积压情况

### 应用统计
```bash
curl http://localhost:8080/api/v1/torrents/stats
```

### Docker 资源监控
```bash
docker stats
```

## 技术栈

- **语言**：Java 21
- **框架**：Spring Boot 3.2
- **消息队列**：Kafka/RedPanda
- **缓存**：Redis
- **数据库**：PostgreSQL
- **构建工具**：Maven
- **容器化**：Docker & Docker Compose

## 许可证

MIT License

## 作者

lihongjie (cn.lihongjie)
