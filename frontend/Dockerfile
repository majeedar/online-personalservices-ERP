# ---- build ---------------------------------------------------------------
FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

# ---- runtime: nginx serves the SPA and proxies the API (same origin, ADR-005)
FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist/ops-frontend/browser /usr/share/nginx/html
EXPOSE 80
