FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm install --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
ENV BFF_OPS_URL=http://bff-ops:8088
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY docker-entrypoint.d/40-logistics-config.sh /docker-entrypoint.d/40-logistics-config.sh
RUN chmod +x /docker-entrypoint.d/40-logistics-config.sh
EXPOSE 8080
