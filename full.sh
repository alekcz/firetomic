chmod +x ./bin/run-integrationtests
npm install -g firebase-tools@10.1.2 
firebase emulators:exec --only database ./bin/run-integrationtests