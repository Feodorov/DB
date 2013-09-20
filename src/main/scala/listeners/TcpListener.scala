package listeners

import akka.actor._
import akka.io.{Tcp, IO}
import java.net.InetSocketAddress
import scala.concurrent.{ExecutionContext, Await}
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import scala.concurrent.duration._
import akka.actor.Terminated

/**
 * Date: 16.09.13
 * Time: 22:28
 * kfeodorov@yandex-team.ru
 */

object TcpListener {

  def props(endpoint: InetSocketAddress): Props = Props(new TcpListener(endpoint))
}

class TcpListener(endpoint: InetSocketAddress) extends Actor with ActorLogging {

  import context.system

  IO(Tcp) ! Tcp.Bind(self, endpoint)

  override def receive: Receive = {
    case Tcp.Connected(remote, _) =>
      log.debug("Remote address {} connected", remote)
      sender ! Tcp.Register(context.actorOf(TcpConnectionHandler.props(remote, sender)))
  }
}

object TcpConnectionHandler {

  def props(remote: InetSocketAddress, connection: ActorRef): Props =
    Props(new TcpConnectionHandler(remote, connection))
}

class TcpConnectionHandler(remote: InetSocketAddress, connection: ActorRef) extends Actor with ActorLogging {

  // We need to know when the connection dies without sending a `Tcp.ConnectionClosed`
  context.watch(connection)

  def receive: Receive = {
    case Tcp.Received(data) =>
      import ExecutionContext.Implicits.global
      val msg = data.utf8String.trim
      log.debug("Received '{}' from remote address {}", msg, remote)

      implicit val timeout = Timeout(2000, MILLISECONDS)
      val future = context.actorSelection("/user/master/storage") ? msg recover {
        case _ => "Timeout error"
      }
      val result = Await.result(future, timeout.duration).asInstanceOf[String]
      sender ! Tcp.Write(ByteString(result))
    case _: Tcp.ConnectionClosed =>
      log.debug("Stopping, because connection for remote address {} closed", remote)
      context.stop(self)
    case Terminated(`connection`) =>
      log.debug("Stopping, because connection for remote address {} died", remote)
      context.stop(self)
  }
}