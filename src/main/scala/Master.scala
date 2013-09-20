import akka.actor.{Props, Actor, ActorLogging}
import java.net.InetSocketAddress
import listeners.{TerminalListener, TcpListener, HttpListener}

object Master{

  def props(path: String, tcpPort: Integer, httpPort: Integer): Props = Props(new Master(path, tcpPort, httpPort))
}

class Master(path: String, tcpPort: Integer, httpPort: Integer) extends Actor with ActorLogging {

  val tcpEndpoint = new InetSocketAddress("localhost", tcpPort)
  context.actorOf(Storage.props(path), "storage")
  context.actorOf(Props[TerminalListener], "terminal-listener")
  context.actorOf(TcpListener.props(tcpEndpoint), "tcp-listener")
  context.actorOf(HttpListener.props("localhost", httpPort), "http-listener")

  def receive: Actor.Receive = {
    case null =>
    case "shutdown" => context.system.shutdown()
    case msg => context.actorSelection("/user/master/terminal-listener") ! msg
  }
}
