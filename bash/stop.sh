#!/usr/bin/env bash
set -euo pipefail

APP_HOME="/mnt/baidu/app"
PID_FILE="$APP_HOME/monitoring.pid"
JAR_NAME="monitoring.jar"

# 1. 停止 Java 服务
if [[ -f "$PID_FILE" ]]; then
    PID=$(<"$PID_FILE")
    if kill -0 "$PID" &>/dev/null; then
        echo " 停止监控服务 (PID=$PID)…"
        kill "$PID"
        # 等待优雅退出
        for i in {1..6}; do
            if ! kill -0 "$PID" &>/dev/null; then
                break
            fi
            sleep 1
        done
        # 强制杀死
        if kill -0 "$PID" &>/dev/null; then
            echo "强制终止 $PID"
            kill -9 "$PID"
        fi
    else
        echo " PID 文件存在但进程 $PID 不在运行，移除 PID 文件。"
    fi
    rm -f "$PID_FILE"
else
    echo " 未找到 PID 文件，尝试通过进程名查找。"
    # 兼容脚本直接跑 jar 的情况
    PID=$(ps -ef | grep "[${JAR_NAME:0:1}]${JAR_NAME:1}" | awk '{print $2}')
    if [[ -n "$PID" ]]; then
        echo "停止进程 $PID"
        kill "$PID" || true
    else
        echo "未找到正在运行的 $JAR_NAME 进程。"
    fi
fi

# 2. 停止 UE 相关进程（可选，若需要）
echo "停止所有 TwinBaseGH5 实例…"
pkill -f TwinBaseGH5 || true

# 3. 清理 signalling 容器
echo "清理 signalling- 容器…"
CONTAINERS=$(docker ps -a --filter "name=^/signalling-" --format "{{.Names}}")
if [[ -n "$CONTAINERS" ]]; then
    echo "$CONTAINERS" | xargs -r docker rm -f
else
    echo "未检测到任何 signalling- 容器。"
fi

echo "停止完成。"
