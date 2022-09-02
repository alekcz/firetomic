chmod +x ./bin/start.sh
clj -T:build clean
clj -T:build uber
firebase emulators:exec --only database ./bin/start.sh