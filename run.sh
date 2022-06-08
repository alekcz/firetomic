#!/usr/bin/env bash
chmod +x ./helper.sh
firebase emulators:exec --only database ./helper.sh