FROM java:8
COPY . /usr/src/yass
WORKDIR /usr/src/yass
RUN start.sh