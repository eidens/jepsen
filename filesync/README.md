# jepsen.filesync

A Clojure library designed to test a simple file sync protocol.

## Usage

FIXME


## Docker

Prerequisites: Docker and docker-compose have to be installed.

1. Clone this repo: git clone git@github.com:eidens/jepsen.git

[This will not work as is: Jepsen creates symlinks which do not work
(?) with Docker]

3. Add the folder containing this repo as a volume to the
jepsen-control container. It should look like this:
  volumes:
    - /path/to/jepsen.filesync:/jepsen/jepsen.filesync

  And the folder containing the filesync repo as a volume to the node
  containers:
  volumes:
    - /path/to/filesync:/jepsen/filesync

2. Set up the instances from the repo:
   cd filesync && ../docker/up.sh

This should start 2 jepsen nodes, a filesync client and server, in
docker containers.

3. Now ssh into jepsen-control container and run the tests:
   docker exec -it jepsen-control bash

4. You should now be in the jepsen-control container. Copy the tests
   to another directory so Jepsen can create symlinks:
   cp -r jepsen.filesync/* jepsen.filesync-copy

5. Run the tests from there:
   cd jepsen.filesync-copy
   lein run test

## License

Copyright © 2018 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
