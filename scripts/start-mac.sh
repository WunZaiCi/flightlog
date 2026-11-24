#!/usr/bin/env sh
# 构建并启动 FlightLog。
# 用法：start-mac.sh [npm 镜像地址]，例如 start-mac.sh https://registry.npmmirror.com
set -eu

cd "$(dirname "$0")/.."

if ! docker info >/dev/null 2>&1; then
  echo "Docker 没有运行。请先启动 Docker Desktop。" >&2
  exit 1
fi

mkdir -p data
if [ ! -f .env ]; then
  echo "提示：没有找到 .env，AI 解析和航班号查询会被禁用（手动录入照常可用）。可复制 .env.example 为 .env 后填写。"
fi

if [ "${1:-}" != "" ]; then
  export NPM_REGISTRY="$1"
fi

echo "正在构建并启动容器..."
docker compose up -d --build

echo "等待服务就绪..."
i=0
while [ $i -lt 60 ]; do
  if curl -fsS http://localhost:8000/api/health >/dev/null 2>&1; then
    echo "FlightLog 已启动：http://localhost:8000"
    exit 0
  fi
  i=$((i + 1))
  sleep 2
done
echo "容器已启动，但服务还没有就绪。可以用 docker compose logs -f 查看日志。"
