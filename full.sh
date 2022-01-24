chmod +x ./bin/run-integrationtests
npm install -g firebase-tools@8.5.0     
firebase emulators:exec --only database ./bin/run-integrationtests