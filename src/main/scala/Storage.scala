import akka.actor.{Props, Actor, ActorLogging}
import com.google.protobuf.TextFormat
import java.io._
import java.net.InetSocketAddress
import model.PersonCard.Person
import org.json.{JSONObject, JSONException}
import scala.Some

/**
 * Date: 16.09.13
 * Time: 23:00
 * kfeodorov@yandex-team.ru
 */

object Storage{

  def props(path: String): Props = Props(new Storage(path))
}

class Storage(path: String) extends Actor with ActorLogging {
  private val MESSAGE_NOT_FOUND = "Card not found"
  private val MESSAGE_MISSING_NAME = "Missing name"
  private val MESSAGE_MISSING_DATA = "Missing data"
  private val MESSAGE_MISSING_CMD = "Missing or unknown command"
  private val MESSAGE_CMD_FAILED = "Failed to perform command"
  private val MESSAGE_CMD_OK = "Success"
  private val PERSON_PHONE = "phone"
  private val PERSON_NAME = "name"
  private val PERSON_OBJECT = "person"
  private val CMD_FIELD = "cmd"
  private val CMD_CREATE = "create"
  private val CMD_READ = "read"
  private val CMD_UPDATE = "update"
  private val CMD_DELETE = "delete"

  def receive: Actor.Receive = {
    case msg => {
      log.debug("Storage. Message received: " + msg)
      try {
        val json = new JSONObject(msg.toString.trim)
        val person = Option(json.getJSONObject(PERSON_OBJECT))
        Option(json.getString(CMD_FIELD)) match {
          case Some(CMD_CREATE) => sender ! create(person)
          case Some(CMD_READ) => sender ! read(person)
          case Some(CMD_UPDATE) => sender ! update(person)
          case Some(CMD_DELETE) => sender ! delete(person)
          case Some(_) => sender ! MESSAGE_MISSING_CMD
          case None => MESSAGE_MISSING_CMD
        }
      } catch {
        case e: JSONException => sender ! "Parsing error. It is not a valid json"
      }
    }
  }

  private def create(o: Option[JSONObject]): String = {
    o match {
      case Some(o) => {
        Option(o.getString(PERSON_NAME)) match {
          case Some(name) => {
            val personBuilder = Person.newBuilder()
            personBuilder.setName(name)
            personBuilder.setPhone(o.optString(PERSON_PHONE))
            val person = personBuilder.build()
            try {
              val os = new FileOutputStream(path + name, false)
              person.writeTo(os)
              "Card " + TextFormat.printToString(person) + " saved successfully"
            } catch {
              case e: IOException => "IO exception"
            } finally {
              os.close()
            }
          }
          case None => MESSAGE_MISSING_NAME
        }
      }
      case None => MESSAGE_MISSING_DATA
    }
  }

  private def read(o: Option[JSONObject]): String = {
    o match {
      case Some(o) => {
        Option(o.getString(PERSON_NAME)) match {
          case Some(name) => {
            val personBuilder = Person.newBuilder()
            try {
              val input = new FileInputStream(path + name)
              val dis = new DataInputStream(input)
              personBuilder.mergeFrom(dis)
              "Card found: " + TextFormat.printToString(personBuilder.build())
            } catch {
              case e: IOException => MESSAGE_NOT_FOUND
            } finally {
              dis.close()
              input.close()
            }
          }
          case None => MESSAGE_MISSING_NAME
        }
      }
      case None => MESSAGE_NOT_FOUND
    }
  }

  private def update(o: Option[JSONObject]): String = {
    delete(o)
    create(o)
  }

  private def delete(o: Option[JSONObject]): String = {
    o match {
      case Some(o) => {
        Option(o.getString(PERSON_NAME)) match {
          case Some(name) => {
            val file = new File(path + name)
            if (file.delete()) {
              MESSAGE_CMD_OK
            } else {
              MESSAGE_CMD_FAILED
            }
          }
          case None => MESSAGE_MISSING_NAME
        }
      }
      case None => MESSAGE_NOT_FOUND
    }

  }
}
