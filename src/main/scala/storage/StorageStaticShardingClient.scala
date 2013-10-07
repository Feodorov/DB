package storage

import akka.actor.{Props, ActorSelection, ActorLogging, Actor}
import org.json.{JSONException, JSONObject}
import scala.None
import scala.concurrent.{ExecutionContext, Await}
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration._
import scala.Some

/**
 * Created with IntelliJ IDEA.
 * User: Kos
 * Date: 07.10.13
 * Time: 8:23
 * To change this template use File | Settings | File Templates.
 */

object StorageStaticShardingClient{
  def props(path: String): Props = Props(new StorageStaticShardingClient(path))
}

class StorageStaticShardingClient(prefix: String) extends Actor with ActorLogging {
  private val shard1MaxKey = context.system.settings.config.getString("storage.shard1.max_key").charAt(0)

  def receive: Actor.Receive = {
    case msg => {
      try {
        val json = new JSONObject(msg.toString.trim)
        Option(json.getJSONObject(Messages.PERSON_OBJECT)) match {
          case Some(p) => {
            Option(p) match {
              case Some(p) => {
                import ExecutionContext.Implicits.global
                implicit val timeout = Timeout(2000, MILLISECONDS)
                val future = getRoute(p.getString(Messages.PERSON_NAME)) ? msg recover {
                  case _ => Messages.MESSAGE_TIMEOUT
                }
                val result = Await.result(future, timeout.duration).asInstanceOf[String]
                sender ! result
              }
              case None => Messages.MESSAGE_MISSING_NAME
            }
          }
          case None => Messages.MESSAGE_MISSING_DATA
        }
      } catch {
        case e: JSONException => sender ! "Parsing error. It is not a valid json"
      }
    }
  }

  private def getRoute(key: String): ActorSelection = {
    val shard1 = context.actorSelection(prefix + "/storage-shard1")
    val shard2 = context.actorSelection(prefix + "/storage-shard2")

    if (key.isEmpty) return shard1
    val firstChar = key.charAt(0)
    if (firstChar <= shard1MaxKey)
      shard1
    else
      shard2
  }
}
