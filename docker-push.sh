#!/usr/bin/env bash

docker image push alekcz/firetomic:$(cat resources/VERSION)
docker image push alekcz/firetomic:latest