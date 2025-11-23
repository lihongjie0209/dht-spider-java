# 多阶段构建 - Stage 1: 构建
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# 复制完整项目结构
COPY pom.xml .
COPY dht-common dht-common
COPY dht-spring-common dht-spring-common
COPY dht-metadata-service dht-metadata-service
COPY dht-mldht/pom.xml dht-mldht/pom.xml
COPY dht-bt-client/pom.xml dht-bt-client/pom.xml

# 构建项目（使用 Maven 缓存）
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -pl dht-metadata-service -am -DskipTests

# Stage 2: 运行时镜像
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 复制构建产物
COPY --from=builder /build/dht-metadata-service/target/dht-metadata-service-*.jar app.jar

# 创建非 root 用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/v1/torrents/health || exit 1

# 暴露端口
EXPOSE 8080

# 去重相关环境变量
ENV DEDUP_ENABLED=true \
  DEDUP_BLOOM_KEY=dht:bloom:persisted

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
