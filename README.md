DB
==

Databases course hometask @ CSC

Description
===========
Server accepts commands via console, tcp port and http.
Server supports 4 major commands: create, read, update, delete.
All commands are accepted in JSON format. 
* To create record simply print this: {"cmd":"create", "person":{"name":"kos","phone":"123"}}
* To read record: {"cmd":"read", "person":{"name":"kos"}}
* To update record: {"cmd":"update", "person":{"name":"kos","phone":"456"}}
* To delete record: {"cmd":"delete", "person":{"name":"kos"}}
* To shutdown server simply print "shutdown" to the console (works only in console, not over http/tcp)

HTTP REST API
=============
JSON is the same as above, without "cmd" field:
* create by key (must not exist): curl -H 'Accept: application/json' -X POST -d '{"person":{"name":"kos","phone":"123"}}' http://localhost:8080/
* read by key (has to exist): curl -H 'Accept: application/json' -X GET -d '{"person":{"name":"kos"}}' http://localhost:8080/
* update/replace by key (has to exist): curl -H 'Accept: application/json' -X PUT -d '{"person":{"name":"kos","phone":"456"}}' http://localhost:8080/
* remove by key (has to exist): curl -H 'Accept: application/json' -X DELETE -d '{"person":{"name":"kos"}}' http://localhost:8080/

Configuration and build
========================
All configuration is in src/main/resources/application.conf. DB runs in two modes - client, and storage shard.
Client configuration is in "client" section. Most interesting values are:
* tcp_port - listens for incoming commands via telnet
* http_port - listens for incoming commands via http
* akka.remote.netty.tcp.hostname and akka.remote.netty.tcp.port - hostname and port to listen for messages from storage shards

Storage shards configurations are in storage.instances array.
* name - any value. commit log for this shard will be prefixed with this name. Also, name is used for remote addressing
* path - folder to store snapshots. This folder must exists, shard doesn't currently check if it is exists on start :)
* min_key and max_key - interval for shard keys
* max_files_on_disk - threshold for compactification of snapshots

Build:
mvn package

Run
===
To run a client instance:
* java -jar ./target/DB-1.0-SNAPSHOT.jar client
To stop it - print "shutdown". There is an infinite Console.readLine() loop that stops after "shutdown" received

To run a shard:
* java -Xmx512m -jar ./target/DB-1.0-SNAPSHOT.jar storage 0
where 0 is the index of shard config in storage.instances section of application.conf. In current config version you can use 0 and 1 values.

Test
=====
Make sure that settings in application.conf are correct (especially path to shard folder). Run 3 instances - one client and two shards:
* java -jar ./target/DB-1.0-SNAPSHOT.jar client
* java -Xmx512m -jar ./target/DB-1.0-SNAPSHOT.jar storage 0
* java -Xmx512m -jar ./target/DB-1.0-SNAPSHOT.jar storage 1
Then run ./static_sharding_run.sh
Sorry, no graceful shutdown yet, and need to think about batching commands



