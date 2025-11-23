.PHONY: help build up down restart logs clean ps stats health

# 默认目标
help:
	@echo "DHT Spider - 可用命令："
	@echo ""
	@echo "  make build    - 构建所有 Docker 镜像"
	@echo "  make up       - 启动所有服务"
	@echo "  make down     - 停止所有服务"
	@echo "  make restart  - 重启所有服务"
	@echo "  make logs     - 查看所有日志"
	@echo "  make ps       - 查看服务状态"
	@echo "  make stats    - 查看统计信息"
	@echo "  make health   - 健康检查"
	@echo "  make clean    - 清理所有数据（危险操作）"
	@echo ""
	@echo "查看特定服务日志："
	@echo "  make logs-mldht"
	@echo "  make logs-btclient"
	@echo "  make logs-metadata"

# 构建镜像
build:
	docker-compose build

# 启动服务
up:
	docker-compose up -d
	@echo "⏳ 等待服务启动..."
	@sleep 10
	@make ps

# 停止服务
down:
	docker-compose down

# 重启服务
restart:
	docker-compose restart

# 查看所有日志
logs:
	docker-compose logs -f

# 查看特定服务日志
logs-mldht:
	docker-compose logs -f dht-mldht

logs-btclient:
	docker-compose logs -f dht-bt-client

logs-metadata:
	docker-compose logs -f dht-metadata-service

logs-redpanda:
	docker-compose logs -f redpanda

logs-redis:
	docker-compose logs -f redis

logs-postgres:
	docker-compose logs -f postgres

# 查看服务状态
ps:
	docker-compose ps

# 查看统计信息
stats:
	@echo "DHT Spider 统计信息："
	@echo ""
	@curl -s http://localhost:8080/api/v1/torrents/stats || echo "❌ 元数据服务未就绪"

# 健康检查
health:
	@echo "健康检查："
	@curl -s http://localhost:8080/api/v1/torrents/health || echo "❌ 元数据服务未就绪"

# 清理所有数据
clean:
	@echo "⚠️  警告：此操作将删除所有数据！"
	@read -p "确认删除所有数据? (yes/no): " confirm; \
	if [ "$$confirm" = "yes" ]; then \
		docker-compose down -v; \
		echo "✅ 所有数据已清理"; \
	else \
		echo "❌ 操作已取消"; \
	fi

# 完整启动（构建+启动）
all: build up
	@echo "✅ 所有服务已启动"
	@echo ""
	@echo "访问地址："
	@echo "  元数据 API: http://localhost:8080"
	@echo "  RedPanda Console: http://localhost:8081"
