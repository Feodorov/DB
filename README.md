DB
==

Databases course hometask @ CSC

Description
===========
Server accepts commands via console, tcp port and http.
No caching is implemented in this version.
Server stores all data in a folder. One entry is stored in one file. Filename equals person name.
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

Run
===
To run server, execute the following command:

java -jar ./target/DB-1.0-SNAPSHOT.jar /Users/kfeodorov/Downloads/DB/ 11111 8080

where:
* /Users/kfeodorov/Downloads/DB/ is a path to folder with DB. Must ends with /
* 11111 - TCP port for listening. You can connect to it later via telnet: >telnet localhost 11111
* 8080 - HTTP port for listening.
