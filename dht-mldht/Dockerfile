# 多阶段构建 - Stage 1: 构建
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# 复制完整项目结构
COPY pom.xml .
COPY dht-common dht-common
COPY dht-mldht dht-mldht
COPY dht-bt-client/pom.xml dht-bt-client/pom.xml
COPY dht-metadata-service/pom.xml dht-metadata-service/pom.xml

# 构建项目
RUN mvn clean package -pl dht-mldht -am -DskipTests

# Stage 2: 运行时镜像
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 复制构建产物
COPY --from=builder /build/dht-mldht/target/dht-mldht-*.jar app.jar

# 创建非 root 用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD pgrep -f java || exit 1

# 暴露 DHT 端口范围 (UDP)
EXPOSE 6881-6900/udp

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
