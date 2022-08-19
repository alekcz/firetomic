chmod +x ./bin/remote.sh
clj -T:build clean
firebase emulators:exec --only database ./bin/start.sh