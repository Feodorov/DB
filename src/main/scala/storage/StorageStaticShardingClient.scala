package storage

import akka.actor.{ActorSelection, ActorLogging, Actor}
import org.json.{JSONException, JSONObject}
import scala.concurrent.Await
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
  private val masterConfig = ConfigFactory.load().getConfig("master")
  private val masterPath = "akka.tcp://DB@" + masterConfig.getString("akka.remote.netty.tcp.hostname") +
    ":" + masterConfig.getInt("akka.remote.netty.tcp.port") + "/user/" + masterConfig.getString("name")
  private val slaves = Map.empty[String, (String, String)]

  for (configObj <- ConfigFactory.load().getObjectList("storage.instances")) {
    val config = configObj.toConfig()
    val hostname = config.getString("akka.remote.netty.tcp.hostname")
    val port = config.getInt("akka.remote.netty.tcp.port")
    val name = config.getString("name")
    val minKey = config.getString("min_key")
    val maxKey = config.getString("max_key")
    val actorPath = "akka.tcp://DB@" + hostname + ":" + port + "/user/" + name
    slaves += (actorPath -> (minKey, maxKey))
  }

  def receive: Actor.Receive = {
    case msg => {
      if (msg.equals("shutdown")) {
        log.debug("shutdown received")
        implicit val timeout = Timeout(3000, MILLISECONDS)
        for (slave <- slaves.keySet) {
          log.debug("shutting down slave {}", slave)
          val future = context.actorSelection(slave) ? "shutdown"
          Await.result(future, timeout.duration).asInstanceOf[String]
        }
        val future = context.actorSelection(masterPath) ? "shutdown"
        Await.result(future, timeout.duration).asInstanceOf[String]
        context.system.shutdown()
      } else {
        var path: String = "unknown path"
        try {
          val name = new JSONObject(msg.toString.trim).optJSONObject(Messages.PERSON_OBJECT).optString(Messages.PERSON_NAME)
          val cmd = new JSONObject(msg.toString.trim).getString(Messages.CMD_FIELD)

          implicit val timeout = Timeout(3000, MILLISECONDS)
          path = getRoute(cmd, name)
          val future = context.actorSelection(path) ? msg.toString
          sender ! Await.result(future, timeout.duration).asInstanceOf[String]
        } catch {
          case e: JSONException => sender ! "Parsing error. It is not a valid json"
          case e: Exception => {
            log.debug("Caught exception: " + e.getMessage)
            sender ! Messages.MESSAGE_SHARD_IS_DOWN + path + " (didn't respond in time)"
          }
        }
      }
    }
  }

  private def getRoute(cmd: String, key: String): String = {
    if (cmd.equals(Messages.CMD_READ)) {
      log.debug("reading from slave")
      val firstChar = key.charAt(0)
      for (entry <- slaves.entrySet()) {
        if (firstChar >= entry.getValue._1.charAt(0) &&
          firstChar < entry.getValue._2.charAt(0)) return entry.getKey
      }
      //send somewhere else :)
      slaves.entrySet().iterator().next().getKey
    } else masterPath
  }
}
