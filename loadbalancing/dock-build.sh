#!/usr/bin/env bash
docker rmi -f sushicutta/loadbalancing
docker build -t sushicutta/loadbalancing .