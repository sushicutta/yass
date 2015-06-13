#!/usr/bin/env bash
docker rmi -f sushicutta/docker-test
docker build -t sushicutta/docker-test .