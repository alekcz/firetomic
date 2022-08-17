#!/usr/bin/env bash
chmod +x ./helper.sh
clj -T:build uber
firebase emulators:exec --only database ./helper.sh