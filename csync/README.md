# jepsen.csync

A Clojure library designed to test a simple file sync protocol.

## Usage with Docker

Prerequisites: Docker and docker-compose have to be installed.

1. Clone this repo: git clone git@github.com:eidens/jepsen.git

[This will not work as is: Jepsen creates symlinks which do not work
(?) with Docker]

2. Add the folder containing the csync repo as a volume to the node
  containers like this:
  volumes:
    - /path/to/csync:/csync/bin

3. Start the docker containers:
   cd docker && sudo ./up.sh

This should start 2 jepsen nodes, one each for a csync client and
server.

3. Now log into the jepsen-control container:
   docker exec -it jepsen-control bash

4. And run the tests from there:
   cd csync && lein run test -n csync_client -n csync_server

## License

Copyright © 2018 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
