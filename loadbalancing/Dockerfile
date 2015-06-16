FROM nginx

MAINTAINER Roman Würsch

RUN rm -v /etc/nginx/nginx.conf

COPY nginx.conf /etc/nginx/nginx.conf

COPY app /etc/nginx/static/app

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]