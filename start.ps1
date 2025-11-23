# DHT Spider - 快速启动脚本 (Windows)

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  DHT Spider - 启动所有服务" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 检查 Docker 是否运行
try {
    docker info | Out-Null
} catch {
    Write-Host "❌ Docker 未运行，请先启动 Docker" -ForegroundColor Red
    exit 1
}

# 检查 docker compose 是否可用
$composeCmd = "docker compose"
try {
    & docker compose version | Out-Null
} catch {
    try {
        & docker-compose version | Out-Null
        $composeCmd = "docker-compose"
    } catch {
        Write-Host "❌ docker-compose 未安装" -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "📦 构建并启动所有服务..." -ForegroundColor Yellow
& $composeCmd up -d --build

Write-Host ""
Write-Host "⏳ 等待服务就绪..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

Write-Host ""
Write-Host "✅ 服务状态：" -ForegroundColor Green
& $composeCmd ps

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  服务访问地址" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "📊 元数据 API:       http://localhost:8080" -ForegroundColor White
Write-Host "🔍 RedPanda Console: http://localhost:8081" -ForegroundColor White
Write-Host "📮 Kafka:            localhost:9092" -ForegroundColor White
Write-Host "🔴 Redis:            localhost:6380" -ForegroundColor White
Write-Host "🐘 PostgreSQL:       localhost:5433" -ForegroundColor White
Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  常用命令" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "查看日志:   $composeCmd logs -f [service_name]" -ForegroundColor White
Write-Host "停止服务:   $composeCmd stop" -ForegroundColor White
Write-Host "重启服务:   $composeCmd restart [service_name]" -ForegroundColor White
Write-Host "删除所有:   $composeCmd down -v" -ForegroundColor White
Write-Host ""
Write-Host "✨ 所有服务已启动！" -ForegroundColor Green
