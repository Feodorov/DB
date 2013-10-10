Configuration
=============

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

Build
=====
* mvn clean package

jar with libs will be in ./target/
Make sure that your protobuf compiler version matches com.google.protobuf.protobuf-java version in pom.xml