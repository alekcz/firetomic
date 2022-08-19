#!/usr/bin/env bash
clj -T:build uber
clj -T:build deploy-image \
      :docker-login ${DOCKER_LOGIN} \
      :docker-password ${DOCKER_PASSWORD} 