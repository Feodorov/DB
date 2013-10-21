package storage

import akka.actor.{ActorSelection, Actor, Props}
import scala.collection.mutable.Map
import com.typesafe.config.ConfigFactory
import scala.collection.JavaConversions._
import org.json.{JSONException, JSONObject}
import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.Await
import akka.pattern.ask

/**
 * Created with IntelliJ IDEA.
 * User: Kos
 * Date: 09.10.13
 * Time: 23:09
 * To change this template use File | Settings | File Templates.
 */

object MasterStorage{
  def props(path: String, maxFiles: Int): Props = Props(new MasterStorage(path, maxFiles))
}

class MasterStorage(path: String, maxFiles: Int) extends Storage(path, maxFiles) {
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

  override def receive: Actor.Receive = {
    case msg => {
      if (msg.toString.trim.equals("shutdown")) {
        log.debug("shutdown received")
        sender ! "OK"
        context.system.shutdown()
      } else {
        val json = new JSONObject(msg.toString.trim)
        val name = json.optJSONObject(Messages.PERSON_OBJECT).optString(Messages.PERSON_NAME)
        val mode = json.optString(Messages.MODE_FIELD)
        val path = getRoute(name)
        //firstly, replicate to slave
        if (mode.equals(Messages.ASYNC_MODE)) {
          //fire-and-forget to slave
          context.actorSelection(path) ! msg.toString
          sender ! Messages.MESSAGE_CMD_OK
        } else {
          //sync mode - wait for shard to complete replication
          try {
            implicit val timeout = Timeout(2000, MILLISECONDS)
            val future = context.actorSelection(path) ? msg.toString
            sender ! Await.result(future, timeout.duration).asInstanceOf[String]
          } catch {
            case e: JSONException => sender ! "Parsing error. It is not a valid json"
            case e: Exception => {
              log.debug("Caught exception: " + e.getMessage)
              //Inform client that command failed
              sender ! Messages.MESSAGE_SHARD_IS_DOWN + path + " (client didn't respond in time). Information saved only to master."
            }
          }
          //secondly, process message by self
          processMessage(msg, true)
        }
      }
    }
  }

  private def getRoute(key: String): String = {
    val firstChar = key.charAt(0)
    for (entry <- slaves.entrySet()) {
      if (firstChar >= entry.getValue._1.charAt(0) &&
        firstChar < entry.getValue._2.charAt(0)) return entry.getKey
    }
    //send somewhere else :)
    slaves.entrySet().iterator().next().getKey
  }
}
