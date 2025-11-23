# 多阶段构建 - Stage 1: 构建
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 复制 POM 文件
COPY pom.xml .
COPY dht-common/pom.xml dht-common/
COPY dht-metadata-service/pom.xml dht-metadata-service/

# 下载依赖（利用 Docker 缓存层）
RUN mvn dependency:go-offline -pl dht-metadata-service -am

# 复制源代码
COPY dht-common/src dht-common/src
COPY dht-metadata-service/src dht-metadata-service/src

# 构建项目
RUN mvn clean package -pl dht-metadata-service -am -DskipTests

# Stage 2: 运行时镜像
FROM eclipse-temurin:17-jre-alpine

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

# 启动应用
ENTRYPOINT ["java", "-jar", "app.jar"]
