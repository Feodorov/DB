package listeners

import akka.actor.{ActorLogging, Actor}
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.{ExecutionContext, Await}
import scala.concurrent.duration._

/**
 * Date: 16.09.13
 * Time: 22:45
 * kfeodorov@yandex-team.ru
 */
class TerminalListener extends Actor with ActorLogging{

  override def receive: Receive = {
    case msg => {
      import ExecutionContext.Implicits.global
      implicit val timeout = Timeout(2000, MILLISECONDS)
      val future = context.actorSelection("/user/master/storage") ? msg recover {
        case _ => "Timeout error"
      }
      val result = Await.result(future, timeout.duration).asInstanceOf[String]
      Console.println(result)
    }
  }
}