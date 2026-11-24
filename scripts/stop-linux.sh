#!/usr/bin/env sh
# 停止 FlightLog。数据库文件保留在 data/ 中，不会被删除。
set -eu

cd "$(dirname "$0")/.."

if ! docker info >/dev/null 2>&1; then
  echo "Docker 没有运行，无需停止。"
  exit 0
fi

# 不加 -v：不删除任何数据
docker compose down
echo "FlightLog 已停止。数据仍保存在 data/flightlog.db。"
