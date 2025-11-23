#!/bin/bash

# DHT Spider 快速启动脚本

set -e

echo "=========================================="
echo "  DHT Spider - 启动所有服务"
echo "=========================================="

# 检查 Docker 是否运行
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker 未运行，请先启动 Docker"
    exit 1
fi

# 检查 docker-compose 是否可用
if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null 2>&1; then
    echo "❌ docker-compose 未安装"
    exit 1
fi

# 使用 docker compose 或 docker-compose
if docker compose version &> /dev/null 2>&1; then
    COMPOSE_CMD="docker compose"
else
    COMPOSE_CMD="docker-compose"
fi

echo ""
echo "📦 构建并启动所有服务..."
$COMPOSE_CMD up -d --build

echo ""
echo "⏳ 等待服务就绪..."
sleep 10

echo ""
echo "✅ 服务状态："
$COMPOSE_CMD ps

echo ""
echo "=========================================="
echo "  服务访问地址"
echo "=========================================="
echo "📊 元数据 API:      http://localhost:8080"
echo "🔍 RedPanda Console: http://localhost:8081"
echo "📮 Kafka:            localhost:9092"
echo "🔴 Redis:            localhost:6380"
echo "🐘 PostgreSQL:       localhost:5433"
echo ""
echo "=========================================="
echo "  常用命令"
echo "=========================================="
echo "查看日志:   $COMPOSE_CMD logs -f [service_name]"
echo "停止服务:   $COMPOSE_CMD stop"
echo "重启服务:   $COMPOSE_CMD restart [service_name]"
echo "删除所有:   $COMPOSE_CMD down -v"
echo ""
echo "✨ 所有服务已启动！"
