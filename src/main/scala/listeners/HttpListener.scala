package listeners

/**
 * Date: 16.09.13
 * Time: 22:29
 * kfeodorov@yandex-team.ru
 */
import akka.actor.{ Actor, ActorLogging, ActorRef, Props}
import akka.io.{ IO, Tcp }
import java.net.InetSocketAddress
import spray.can.Http
import akka.util.Timeout
import scala.concurrent.duration._
import akka.actor.Terminated
import scala.concurrent.{ExecutionContext, Await}
import akka.pattern.ask
import storage.Messages
import org.json.JSONObject
import spray.http._


object HttpListener {

  def props(host: String, port: Int): Props =
    Props(new HttpListener(host, port))
}

class HttpListener(host: String, port: Int) extends Actor with ActorLogging {

  import context.system

  IO(Http) ! Http.Bind(self, host, port)

  override def receive: Receive = {
    case Http.Connected(remote, _) =>
      log.debug("Remote address {} connected", remote)
      sender ! Http.Register(context.actorOf(HttpConnectionHandler.props(remote, sender)))
  }
}

object HttpConnectionHandler {
  def props(remote: InetSocketAddress, connection: ActorRef): Props =
    Props(new HttpConnectionHandler(remote, connection))
}

class HttpConnectionHandler(remote: InetSocketAddress, connection: ActorRef) extends Actor with ActorLogging {

  // We need to know when the connection dies without sending a `Tcp.ConnectionClosed`
  context.watch(connection)

  def receive: Receive = {
    case HttpRequest(method, uri, _, entity, _) => {
      if (entity.asString.equals("shutdown")) {
        context.actorSelection("/user/storage-client") ! "shutdown"
      } else {
        val cmd = method match {
          case HttpMethods.GET => Messages.CMD_READ
          case HttpMethods.POST => Messages.CMD_CREATE
          case HttpMethods.PUT => Messages.CMD_UPDATE
          case HttpMethods.DELETE => Messages.CMD_DELETE
        }

        import ExecutionContext.Implicits.global
        implicit val timeout = Timeout(3000, MILLISECONDS)

        val future = context.actorSelection("/user/storage-client") ?
          new JSONObject(entity.asString).put(Messages.CMD_FIELD, cmd) recover {
          case _ => "Timeout error"
        }
        val result = Await.result(future, timeout.duration).asInstanceOf[String]
        sender ! HttpResponse(entity = result)
      }
    }
    case _: Tcp.ConnectionClosed =>
      log.debug("Stopping, because connection for remote address {} closed", remote)
      context.stop(self)
    case Terminated(`connection`) =>
      log.debug("Stopping, because connection for remote address {} died", remote)
      context.stop(self)
  }
}

