#!/usr/bin/env bash
set -euo pipefail

APP_HOME="/mnt/baidu/app"
JAR="$APP_HOME/monitoring.jar"
CONFIG="$APP_HOME/application.properties"
LOG="$APP_HOME/monitoring.log"
PID_FILE="$APP_HOME/monitoring.pid"
BACKUP_LOG="$APP_HOME/monitoring.log.$(date +%Y%m%d%H%M%S)"

# 1. 单实例检查
if [[ -f "$PID_FILE" ]]; then
    OLD_PID=$(<"$PID_FILE")
    if kill -0 "$OLD_PID" &>/dev/null; then
        echo "监控服务已在运行 (PID=$OLD_PID)，请先停止再启动。"
        exit 1
    else
        echo "检测到遗留的 PID 文件，移除并继续启动。"
        rm -f "$PID_FILE"
    fi
fi

# 2. 日志轮转：若旧日志存在，则备份
if [[ -f "$LOG" ]]; then
    echo "备份旧日志到 $BACKUP_LOG"
    mv "$LOG" "$BACKUP_LOG"
fi

# 3. 启动服务，重定向 stdout+stderr 到日志
echo "启动监控服务..."
nohup java -Xms4G -Xmx8G -jar "$JAR" \
    --spring.config.location="$CONFIG" \
    --REDIS_TYPE=STANDALONE \
    >> "$LOG" 2>&1 &

# 4. 保存 PID
echo $! > "$PID_FILE"
echo "服务已启动 (PID=$(cat $PID_FILE)), 日志: $LOG"

# 5. 优雅关闭信号处理
trap 'echo "收到停止信号，退出..."; kill "$(cat $PID_FILE)"; exit 0' TERM INT

