ARG NODE_IMAGE=node:22.12.0-alpine
ARG NGINX_IMAGE=nginx:1.27.4-alpine

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

EXPOSE 80
