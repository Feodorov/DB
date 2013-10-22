DB
==

Databases course hometask @ CSC

Description
===========
Server accepts commands via console, tcp port and http.
Server supports 4 major commands: create, read, update, delete.
All commands are accepted in JSON format.

Console and Telnet API
======================
* To create record simply print this: {"cmd":"create", "person":{"name":"kos","phone":"123"}}
* To read record: {"cmd":"read", "person":{"name":"kos"}}
* To update record: {"cmd":"update", "person":{"name":"kos","phone":"456"}}
* To delete record: {"cmd":"delete", "person":{"name":"kos"}}
* To shutdown server simply print "shutdown" to the console/telnet

HTTP REST API
=============
JSON is the same as above, without "cmd" field:
* create by key (must not exist): curl -H 'Accept: application/json' -X POST -d '123' http://localhost:8080/async/kos
* read by key (has to exist): curl -H 'Accept: application/json' -X GET http://localhost:8080/async/kos
* update/replace by key (has to exist): curl -H 'Accept: application/json' -X PUT -d '456' http://localhost:8080/async/kos
* remove by key (has to exist): curl -H 'Accept: application/json' -X DELETE http://localhost:8080/kos
* To shutdown just POST/GET/DELETE/PUT/WHATEVER "shutdown"
* You can add /async to the root of path to run in async mode (master doesn't wait for response from slave)

Configuration and build
========================
See INSTALL.md

Run
===
To run a client instance:
* java -jar ./target/DB-1.0-SNAPSHOT.jar client
To stop it - print "shutdown". There is an infinite Console.readLine() loop that stops after "shutdown" received. Also client will shutdown master and slaves

To run a master:
* java -Xmx512m -jar ./target/DB-1.0-SNAPSHOT.jar master

To run a shard:
* java -Xmx512m -jar ./target/DB-1.0-SNAPSHOT.jar slave 0
where 0 is the index of shard config in storage.instances section of application.conf. In current config version you can use 0 and 1 values.

To run compact tool:
* java -Xmx512m -jar ./target/DB-1.0-SNAPSHOT.jar compact /abs/path/to/storage

Test
=====
Make sure that settings in application.conf are correct (especially path to shard folder). Run client:
* java -jar ./target/DB-1.0-SNAPSHOT.jar client
* Then run ./static_sharding_run.sh or ./static_sharding_graceful_shutdown_run.sh - these scripts will create and kill master and slaves automatically

Update:
=======
New optional flag "mode" in each command added:
{"cmd":"create", "mode":"async", "person":{"name":"myName","phone":"cxae"}}
Possible values:
* async - master sends create/update/delete to slave, but do not check response - actually we will not know the result
If "mode" is omitted - master waits for response from slave and return it to the client



