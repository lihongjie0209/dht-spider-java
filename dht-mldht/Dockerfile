# 多阶段构建 - Stage 1: 构建
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# 复制 POM 文件
COPY pom.xml .
COPY dht-common/pom.xml dht-common/
COPY dht-mldht/pom.xml dht-mldht/

# 下载依赖（利用 Docker 缓存层）
RUN mvn dependency:go-offline -pl dht-mldht -am

# 复制源代码
COPY dht-common/src dht-common/src
COPY dht-mldht/src dht-mldht/src

# 构建项目
RUN mvn clean package -pl dht-mldht -am -DskipTests

# Stage 2: 运行时镜像
FROM eclipse-temurin:17-jre-alpine

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
