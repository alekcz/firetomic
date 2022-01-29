<p align="left">
    <img alt="Firetomic" src="./resources/wordmark-dark.svg" height="128em">
</p>

[![CI](https://github.com/alekcz/firetomic/actions/workflows/main.yml/badge.svg)](https://github.com/alekcz/firetomic/actions/workflows/main.yml) [![codecov](https://codecov.io/gh/alekcz/firetomic/branch/main/graph/badge.svg?token=UkLQlpnfbp)](https://codecov.io/gh/alekcz/firetomic)   

## Deploy Firetomic

[![Deploy to DO](https://www.deploytodo.com/do-btn-blue.svg)](https://cloud.digitalocean.com/apps/new?repo=https://github.com/alekcz/firetomic/tree/main&refcode=a0cfd79e40a2)  

We both get credits for DigitalOcean if you end using their services so be a mate.   

Or you could deploy to Heroku  

[![Deploy](https://www.herokucdn.com/deploy/button.svg)](https://heroku.com/deploy?template=https://github.com/alekcz/firetomic/tree/main)

## Build Firetomic

`clj -T:build uber` builds an uberjar into the `target/`-directory.

## Run Firetomic

Run firetomic in locally:

`clj -X:run`

## Configuring Firetomic
### File Configuration

Firetomic loads configuration from `resources/config.edn` relative to the
current directory. This file has a number of options and overwrites all other
configuration given via environment or properties. Below you can find an example
to configure both Datahike and the server.
```
{:databases [{:store {:backend :firebase 
                      :db "https://project-id.firebaseio.com" 
                      :root "users"
                      :env "FIRETOMIC_FIREBASE_AUTH"}
              :name "users"
              :keep-history? true
              :schema-flexibility :write}]
 :server {:port 3000
          :join? false
          :dev-mode false
          :token :yourtoken
          :loglevel :debug}}
```

### Configuration via Environment and Properties

Firetomic can also be configured via environment variables and java system
properties. Please take a look at the [configuration of Datahike](https://github.com/replikativ/datahike/blob/development/doc/config.md) to get an
overview of the number of possible configuration options regarding the database.
To configure the server please see the options below. Like in Datahike they are
read via the [environ library by weavejester](https://github.com/weavejester/environ).
Please provide the logging level without colon. Beware that a configuration file
overwrites the values from environment and properties.

envvar                    | default
--------------------------|-------------
FIRETOMIC_PORT            | 4000
FIRETOMIC_JOIN            | false
FIRETOMIC_LOGLEVEL        | :warn
FIRETOMIC_DEV_MODE        | false
FIRETOMIC_TOKEN           | --
FIRETOMIC_NAME            | --
FIRETOMIC_FIREBASE_URL    | http://localhost:9000
FIRETOMIC_FIREBASE_AUTH   | --


### Authentication

You can authenticate to Firetomic with a token specified via configuration. Please
then send the token within your request headers as `authentication: token <yourtoken>`.
If you don't want to use authentication during development you can set dev-mode to true
in your configuration and just omit the authentication-header. Please be aware that your
Firetomic might be running publicly accessible and then your data might be read
by anyone and the server might be misused if no authentication is active.

### Logging

We are using the [library taoensso.timbre by Peter Taoussanis](https://github.com/ptaoussanis/timbre/) to provide
meaningful log messages. Please set the loglevel that you prefer via means
of configuration below. The possible levels are sorted in order from least
severe to most severe:
- trace
- debug
- info
- warn
- error
- fatal
- report

# Roadmap

## Release 0.1.0
- [ ] JSON support #18
- [x] Token authentication
- [ ] Implement db-tx #25
- [ ] Improve documentation #23
- [ ] Improve error messages #24
- [ ] [Clojure client](https://github.com/replikativ/datahike-client/)
- [ ] [Clojurescript client](https://github.com/replikativ/datahike-client/)

## Release 0.2.0
- [ ] Import/Export/Backup
- [ ] Metrics
- [ ] Subscribe to transactions
- [ ] Implement query engine in client

# License

Copyright © 2022 Konrad Kühne, Timo Kramer, Alexander Oloo

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
