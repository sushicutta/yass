#!/usr/bin/env bash
docker run -d -p 9091:9090 --name yass-node-1 sushicutta/docker-test
docker run -d -p 9092:9090 --name yass-node-2 sushicutta/docker-test