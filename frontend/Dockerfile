# Frontend image: build the SPA with Node, serve it with Caddy.
#
# Caddy also reverse-proxies /api to the backend, which is the whole point of this image. The SPA
# calls relative paths (`/api/v1/...`) everywhere, so serving both from ONE origin means no CORS
# configuration, no API base URL to inject at build time, and no code change to deploy it. Split
# them across two hostnames and every one of those problems appears at once.

FROM node:20-alpine AS build
WORKDIR /build

# package files first: this layer is cached unless dependencies actually change, so an edit to a
# component does not re-download node_modules.
COPY package.json package-lock.json* ./
RUN npm ci || npm install

COPY . .
RUN npm run build


FROM caddy:2-alpine AS runtime
COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /build/dist /srv
EXPOSE 80
