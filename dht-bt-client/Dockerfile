# 多阶段构建 - Stage 1: 构建
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# 复制完整项目结构
COPY pom.xml .
COPY dht-common dht-common
COPY dht-spring-common dht-spring-common
COPY dht-bt-client dht-bt-client
COPY dht-mldht/pom.xml dht-mldht/pom.xml
COPY dht-metadata-service/pom.xml dht-metadata-service/pom.xml

# 构建项目（使用 Maven 缓存）
RUN --mount=type=cache,target=/root/.m2 \
    mvn clean package -pl dht-bt-client -am -DskipTests

# Stage 2: 运行时镜像
FROM eclipse-temurin:21-jre

WORKDIR /app

 # 复制构建产物
COPY --from=builder /build/dht-bt-client/target/dht-bt-client-*.jar app.jar

# 预创建固定 native 与临时目录，避免在 /tmp 上的 noexec/权限问题
RUN mkdir -p /opt/libtorrent /opt/tmp /app/bt-temp \
  && addgroup --system appgroup \
  && adduser --system --ingroup appgroup appuser \
  && chown -R appuser:appgroup /opt/libtorrent /opt/tmp /app

USER appuser

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD pgrep -f java || exit 1

# 暴露 BT 客户端端口范围 (TCP/UDP)
EXPOSE 6901-6950
EXPOSE 6901-6950/udp

# 去重相关环境变量
ENV DEDUP_ENABLED=true \
  DEDUP_BLOOM_KEY=dht:bloom:downloaded

# JVM 与 libtorrent4j 环境变量
ENV JAVA_OPTS="-Djava.library.path=/opt/libtorrent -Djava.io.tmpdir=/opt/tmp -XX:+UseContainerSupport -XX:MaxRAMPercentage=75"

# 可选：预热（如需在运行层预先加载，可取消注释）
# RUN java $JAVA_OPTS -cp app.jar cn.lihongjie.dht.btclient.service.LibtorrentWarmup || true

# 启动应用（允许覆盖 JAVA_OPTS）
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
