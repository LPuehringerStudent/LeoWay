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
