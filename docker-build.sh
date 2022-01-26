#!/usr/bin/env bash

docker build -t alekcz/firetomic:$(cat resources/VERSION) .
docker build -t alekcz/firetomic:latest .