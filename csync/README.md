# jepsen.csync

Tests a simple file sync protocol.

## Model

The current test uses a Knossos register as its model.

The syncing of a file from client to server is treated as a register
write.

The test always uses the same filename on the client (but different
content). A register read simply tries to read the content of the file
with that same name on the server.

## Usage with Docker

Prerequisites: Docker and docker-compose have to be installed and
a csync executable must be available.

1. Clone this repo: git clone https://github.com/eidens/jepsen.git

2. Add the folder containing the csync executable in
  docker/docker-compose.yml as a volume to the node containers like
  this:

  volumes:
    - /path/to/dir/with/csync/binary:/csync/bin

  [That path is already configured with a path that works for
  me. You'll likely have to change that.]

  !! Leave the path in the nodes (i.e. everything after the ':')
  unchanged. !!

3. Start the docker containers:

   [run this from the jepsen/docker directory]
   sudo ./up.sh

   This should start 3 jepsen nodes, one each for a csync client and
   server and one control node.

4. Now log into the jepsen-control container...

   docker exec -it jepsen-control bash

5. ... and run the tests from there:

   cd csync
   lein run test -n csync_client -n csync_server

   [the -n options tell jepsen the non-standard node names configured
   in docker-compose.yml]


Right now this only does a few writes and reads without a nemesis.

## License

Copyright © 2018 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
