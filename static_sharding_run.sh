#!/bin/bash

BIGSTRING="Actors are very lightweight concurrent entities. They process messages asynchronously using an event-driven receive loop. Pattern matching against messages is a convenient way to express an actors behavior. They raise the abstraction level and make it much easier to write, test, understand and maintain concurrent and/or distributed systems. You focus on workflow—how the messages flow in the system—instead of low level primitives like threads, locks and socket IO"
for i in {1..1000000}; do
    CREATE="{\"person\":{\"name\":\"akos$i\",\"phone\":\"$i$BIGSTRING\"}}"
    READ="{\"person\":{\"name\":\"akos$i\"}}"
    CREATE_RESPONSE=$(curl -H 'Accept: application/json' -X POST -d "$CREATE" http://localhost:8080/ 2> /dev/null)
    READ_RESPONSE=$(curl -H 'Accept: application/json' -X GET -d "$READ" http://localhost:8080/ 2> /dev/null)
    if [ "$CREATE_RESPONSE" = "Success" ]
   	then
   		echo "Correct for create request #$i"
   	else
   		echo "Wrong create response: $CREATE_RESPONSE"
   		exit
   	fi
   	if [ "$READ_RESPONSE" = "$i$BIGSTRING" ]
   	then
   		echo "Correct for read request #$i"
   	else
   		echo "Wrong create response: $READ_RESPONSE"
   		exit
   	fi
done
