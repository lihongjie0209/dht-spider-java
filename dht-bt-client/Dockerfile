# 多阶段构建 - Stage 1: 构建
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 复制 POM 文件
COPY pom.xml .
COPY dht-common/pom.xml dht-common/
COPY dht-bt-client/pom.xml dht-bt-client/

# 下载依赖（利用 Docker 缓存层）
RUN mvn dependency:go-offline -pl dht-bt-client -am

# 复制源代码
COPY dht-common/src dht-common/src
COPY dht-bt-client/src dht-bt-client/src

# 构建项目
RUN mvn clean package -pl dht-bt-client -am -DskipTests

# Stage 2: 运行时镜像
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 复制构建产物
COPY --from=builder /build/dht-bt-client/target/dht-bt-client-*.jar app.jar

# 创建 BT 临时存储目录
RUN mkdir -p /app/bt-temp && \
    addgroup -S appgroup && adduser -S appuser -G appgroup && \
    chown -R appuser:appgroup /app

USER appuser

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD pgrep -f java || exit 1

# 暴露 BT 客户端端口范围 (TCP/UDP)
EXPOSE 6901-6950
EXPOSE 6901-6950/udp

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
