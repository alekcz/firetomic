chmod +x ./bin/start.sh
clj -T:build clean
firebase emulators:exec --only database ./bin/start.sh