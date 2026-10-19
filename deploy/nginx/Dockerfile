# ============================================================
# 前端 + Nginx 镜像
#   阶段 1：Node 编译前端 dist
#   阶段 2：nginx 托管 dist（缓存/压缩），接口反代 -> app:8088
# 构建上下文必须是仓库根目录（compose 里 dockerfile 指到这里）
# ============================================================
FROM node:20-alpine AS fe-build
WORKDIR /fe
COPY hotelWeb/package.json hotelWeb/package-lock.json ./
RUN npm ci
COPY hotelWeb/ ./
RUN npm run build

FROM nginx:1.27-alpine
COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=fe-build /fe/dist /usr/share/nginx/html
EXPOSE 80
