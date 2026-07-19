ARG NODE_IMAGE=node:22.12.0-alpine@sha256:51eff88af6dff26f59316b6e356188ffa2c422bd3c3b76f2556a2e7e89d080bd
ARG NGINX_IMAGE=nginx:1.27.4-alpine@sha256:4ff102c5d78d254a6f0da062b3cf39eaf07f01eec0927fd21e219d0af8bc0591

FROM ${NODE_IMAGE} AS build

WORKDIR /app

ARG VITE_API_URL=/api/v1
ARG VITE_INQUIRY_BOARD_URL=inquiry
ARG VITE_COMMIT_HASH=docker

ENV VITE_API_URL=${VITE_API_URL}
ENV VITE_INQUIRY_BOARD_URL=${VITE_INQUIRY_BOARD_URL}
ENV VITE_COMMIT_HASH=${VITE_COMMIT_HASH}

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM ${NGINX_IMAGE}

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
RUN touch /var/run/nginx.pid \
    && chown -R nginx:nginx /var/cache/nginx /usr/share/nginx/html \
    && chown nginx:nginx /var/run/nginx.pid

EXPOSE 8080

USER nginx
