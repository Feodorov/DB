package listeners

import akka.actor.{ActorLogging, Actor}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.{ExecutionContext, Await}
import scala.concurrent.duration._
import storage.Messages

/**
 * Date: 16.09.13
 * Time: 22:45
 * kfeodorov@yandex-team.ru
 */
class TerminalListener extends Actor with ActorLogging{

  override def receive: Receive = {
    case msg => {
      if (msg.equals("shutdown")) {
        context.actorSelection("/user/storage-client") ! "shutdown"
      } else {
        import ExecutionContext.Implicits.global
        implicit val timeout = Timeout(3000, MILLISECONDS)
        val future = context.actorSelection("/user/storage-client") ? msg recover {
          case _ => Messages.MESSAGE_TIMEOUT
        }
        val result = Await.result(future, timeout.duration).asInstanceOf[String]
        Console.println(result)
      }
    }
  }
}
