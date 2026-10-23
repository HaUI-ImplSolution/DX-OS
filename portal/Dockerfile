FROM nginx:1.27-alpine
ENV PORTAL_BI_URL="" PORTAL_CHAT_URL="" PORTAL_MINIO_URL=""
COPY index.html /usr/share/nginx/html/index.html
COPY nginx.conf /etc/nginx/templates/default.conf.template
EXPOSE 80
