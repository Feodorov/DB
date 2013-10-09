package storage

import akka.actor.{ActorSelection, ActorLogging, Actor}
import org.json.{JSONException, JSONObject}
import scala.concurrent.{ExecutionContext, Await}
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import scala.collection.JavaConversions._
import scala.collection.mutable.Map

/**
 * Created with IntelliJ IDEA.
 * User: Kos
 * Date: 07.10.13
 * Time: 8:23
 * To change this template use File | Settings | File Templates.
 */

class StorageStaticShardingClient extends Actor with ActorLogging {
  private val shards = Map.empty[String, (String, String)]

  for (configObj <- ConfigFactory.load().getObjectList("storage.instances")) {
    val config = configObj.toConfig()
    val hostname = config.getString("akka.remote.netty.tcp.hostname")
    val port = config.getInt("akka.remote.netty.tcp.port")
    val name = config.getString("name")
    val minKey = config.getString("min_key")
    val maxKey = config.getString("max_key")
    val actorPath = "akka.tcp://DB@" + hostname + ":" + port + "/user/" + name
    shards += (actorPath -> (minKey, maxKey))
  }

  def receive: Actor.Receive = {
    case msg => {
      if (msg.equals("shutdown")) {
        log.debug("shutdown received")
        for (shard <- shards.keySet) {
          context.actorSelection(shard) ! "shutdown"
        }
        context.system.shutdown()
      }
      try {
        val name = new JSONObject(msg.toString.trim).optJSONObject(Messages.PERSON_OBJECT).optString(Messages.PERSON_NAME)
        implicit val timeout = Timeout(2000, MILLISECONDS)
        val future = getRoute(name) ? msg.toString
        sender ! Await.result(future, timeout.duration).asInstanceOf[String]
      } catch {
        case e: JSONException => sender ! "Parsing error. It is not a valid json"
        case e: Exception => {log.debug("Caught exception: " + e.getMessage); sender ! Messages.MESSAGE_SHARD_IS_DOWN }
      }
    }
  }

  private def getRoute(key: String): ActorSelection = {
    val firstChar = key.charAt(0)
    for (entry <- shards.entrySet()) {
      if (firstChar >= entry.getValue._1.charAt(0) &&
        firstChar < entry.getValue._2.charAt(0)) return context.actorSelection(entry.getKey)
    }
    //send somewhere else :)
    context.actorSelection(shards.entrySet().iterator().next().getKey)
  }
}
