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

echo "********************"
echo "Populating shard 0..."
for i in {1..10}; do
    echo "-------------------"
    echo "iteration $i of 10"
    CREATE_RESPONSE=$(curl -H 'Accept: application/json' -X POST -d "aaa$i$BIGSTRING" http://localhost:8080/akos$i 2> /dev/null)
    READ_RESPONSE=$(curl -H 'Accept: application/json' -X GET http://localhost:8080/akos$i 2> /dev/null)
    if [ "$CREATE_RESPONSE" != "Success" ]
   	then
   		echo "Wrong create response: $CREATE_RESPONSE"
    else
      echo "create - OK"
   	fi
   	if [ "$READ_RESPONSE" != "aaa$i$BIGSTRING" ]
   	then
   		echo "Wrong read response: $READ_RESPONSE"
    else
      echo "read - OK"
   	fi
done
echo "Done"

echo "*************************"
echo "Populating shard 1..."
for i in {1..10}; do
    echo "-------------------"
    echo "iteration $i of 10"
    CREATE_RESPONSE=$(curl -H 'Accept: application/json' -X POST -d "zzz$i$BIGSTRING" http://localhost:8080/zkos$i 2> /dev/null)
    READ_RESPONSE=$(curl -H 'Accept: application/json' -X GET http://localhost:8080/zkos$i 2> /dev/null)
    if [ "$CREATE_RESPONSE" != "Success" ]
    then
      echo "Wrong create response: $CREATE_RESPONSE"
    else
      echo "create - OK"
    fi
    if [ "$READ_RESPONSE" != "zzz$i$BIGSTRING" ]
    then
      echo "Wrong create response: $READ_RESPONSE"
    else
      echo "read - OK"
    fi
done
echo "Done"
echo "*************************"
echo "Kill shard 1"
kill %3
sleep 3s

echo "*************************"
echo "Testing actual shutdown"
for i in {1..10}; do
    echo "-------------------"
    echo "iteration $i of 10"
    AREAD="{\"person\":{\"name\":\"akos$i\"}}"
    ZREAD="{\"person\":{\"name\":\"zkos$i\"}}"

    AREAD_RESPONSE=$(curl -H 'Accept: application/json' -X GET http://localhost:8080/akos$i 2> /dev/null)
    ZREAD_RESPONSE=$(curl -H 'Accept: application/json' -X GET http://localhost:8080/zkos$i 2> /dev/null)
    if [[ $ZREAD_RESPONSE != Shard* ]]
    then
      echo "Wrong create response: $ZREAD_RESPONSE"
    else
      echo "read from shard 1 - shard is down as expected"
    fi
    if [ "$AREAD_RESPONSE" != "aaa$i$BIGSTRING" ]
    then
      echo "Wrong create response: $AREAD_RESPONSE"
    else
      echo "read from shard 0 - OK"
    fi
done

echo "Killing the rest"
kill %2
kill %1
rm *.lock
echo "Done. Bye"
