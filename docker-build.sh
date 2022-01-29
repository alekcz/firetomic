#!/usr/bin/env bash

docker build -t alekcz/firetomic:$(cat resources/VERSION) --file build.Dockerfile .
docker build -t alekcz/firetomic:latest --file build.Dockerfile .