# syntax=docker/dockerfile:1
# 多阶段构建：Node 构建前端 → Maven 构建 jar（前端打进 static/）→ JRE 运行
# 中国大陆网络：docker compose build --build-arg NPM_REGISTRY=https://registry.npmmirror.com

# ---- 1. 前端静态导出 ----
FROM node:24-alpine AS frontend
ARG NPM_REGISTRY=https://registry.npmjs.org
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --registry="${NPM_REGISTRY}" --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---- 2. 后端 jar ----
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /src/backend
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
COPY backend/src src
# pom.xml 会把 ../frontend/out 复制进 jar 的 static/
COPY --from=frontend /src/frontend/out /src/frontend/out
# 测试和格式检查由 mvnw verify 单独执行，镜像构建不重复跑
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B -q package -DskipTests \
    && cp target/flightlog-*.jar /src/app.jar

# ---- 3. 运行 ----
FROM eclipse-temurin:25-jre
RUN groupadd --system --gid 10001 flightlog \
    && useradd --system --uid 10001 --gid flightlog --home-dir /app --shell /usr/sbin/nologin flightlog \
    && mkdir -p /app/data \
    && chown -R flightlog:flightlog /app
WORKDIR /app
COPY --from=backend --chown=flightlog:flightlog /src/app.jar /app/app.jar

# 数据库在挂载的 /app/data；容器内监听所有地址，宿主机端口由 compose 只绑定 127.0.0.1
ENV FLIGHTLOG_DATA_DIR=/app/data \
    SERVER_ADDRESS=0.0.0.0

USER flightlog
EXPOSE 8000
VOLUME ["/app/data"]
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
