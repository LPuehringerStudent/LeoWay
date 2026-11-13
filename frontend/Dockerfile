# Production image of the LeoWay web app: the Angular build behind nginx, which also proxies /api.
#   docker build -t leoway-web frontend
FROM node:26 AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build

FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist/leoway/browser /usr/share/nginx/html
# Serves the app itself, not only "container running", so a hung nginx fails the deploy health gate (#83).
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s \
  CMD wget -q -O /dev/null http://127.0.0.1/index.html || exit 1
