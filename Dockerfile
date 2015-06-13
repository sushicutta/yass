FROM java:8

MAINTAINER Roman Würsch

COPY . /usr/src/yass

WORKDIR /usr/src/yass

EXPOSE 9090

CMD ["./start-in-docker.sh"]