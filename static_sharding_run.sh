#!/bin/bash

rm storage-shard0commitLog.txt
rm storage-shard1commitLog.txt
rm storage-mastercommitLog.txt

read -p "Make sure that you've launched the client: java -jar ./target/DB-1.0-SNAPSHOT.jar client. Press [Enter]..."

java -Xmx512m -jar ./target/DB-1.0-SNAPSHOT.jar master &
java -Xmx512m -jar ./target/DB-1.0-SNAPSHOT.jar slave 0 &
java -Xmx512m -jar ./target/DB-1.0-SNAPSHOT.jar slave 1 &
sleep 3s

BIGSTRING="Actors are very lightweight concurrent entities. They process messages asynchronously using an event-driven receive loop. Pattern matching against messages is a convenient way to express an actors behavior. They raise the abstraction level and make it much easier to write, test, understand and maintain concurrent and/or distributed systems. You focus on workflow—how the messages flow in the system—instead of low level primitives like threads, locks and socket IO"
for i in {1..5000}; do
    ACREATE_RESPONSE=$(curl -H 'Accept: application/json' -X POST -d "a$i$BIGSTRING" http://localhost:8080/akos$i 2> /dev/null)
    AREAD_RESPONSE=$(curl -H 'Accept: application/json' -X GET http://localhost:8080/akos$i 2> /dev/null)
    if [ "$ACREATE_RESPONSE" = "Success" ]
   	then
   		echo "Correct for A create request #$i"
   	else
   		echo "Wrong create response: $ACREATE_RESPONSE"
   		exit
   	fi
   	if [ "$AREAD_RESPONSE" = "a$i$BIGSTRING" ]
   	then
   		echo "Correct for A read request #$i"
   	else
   		echo "Wrong create response: $AREAD_RESPONSE"
   		exit
   	fi
    ZCREATE="{\"person\":{\"name\":\"zkos$i\",\"phone\":\"z$i$BIGSTRING\"}}"
    ZREAD="{\"person\":{\"name\":\"zkos$i\"}}"
    ZCREATE_RESPONSE=$(curl -H 'Accept: application/json' -X POST -d "z$i$BIGSTRING" http://localhost:8080/zkos$i 2> /dev/null)
    ZREAD_RESPONSE=$(curl -H 'Accept: application/json' -X GET http://localhost:8080/zkos$i 2> /dev/null)
    if [ "$ZCREATE_RESPONSE" = "Success" ]
    then
        echo "Correct for Z create request #$i"
    else
        echo "Wrong create response: $ZCREATE_RESPONSE"
        exit
    fi
    if [ "$ZREAD_RESPONSE" = "z$i$BIGSTRING" ]
    then
        echo "Correct for Z read request #$i"
    else
        echo "Wrong create response: $ZREAD_RESPONSE"
        exit
    fi
done

kill %1
kill %2
kill %3
rm *.lock