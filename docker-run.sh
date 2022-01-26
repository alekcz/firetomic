#!/usr/bin/env bash

docker run \
  --env FIRETOMIC_NAME \
  --env FIRETOMIC_FIREBASE_URL \
  --env FIRETOMIC_FIREBASE_AUTH \
  --env FIRETOMIC_PORT \
  --env FIRETOMIC_TOKEN \
  -p $FIRETOMIC_PORT:$FIRETOMIC_PORT \
  alekcz/firetomic:latest 