import akka.actor.{Props, Actor, ActorLogging}
import java.io._
import java.util.Date
import model.PersonCard.Person
import org.json.{JSONObject, JSONException}
import scala.Some
import scala.collection.mutable._
import scala.collection.JavaConversions.{asJavaCollection=>_,_}

/**
 * Date: 16.09.13
 * Time: 23:00
 * kfeodorov@yandex-team.ru
 */

object Storage{

  def props(path: String): Props = Props(new Storage(path))
}

class Storage(path: String) extends Actor with ActorLogging {
  private val MESSAGE_NOT_FOUND = "Value not found"
  private val MESSAGE_MISSING_NAME = "Missing name"
  private val MESSAGE_MISSING_DATA = "Missing data"
  private val MESSAGE_MISSING_CMD = "Missing or unknown command"
  private val MESSAGE_CMD_DUPLICATE = "Failed. Key already exists"
  private val MESSAGE_CMD_OK = "Success"
  private val PERSON_PHONE = "phone"
  private val PERSON_NAME = "name"
  private val PERSON_OBJECT = "person"
  private val CMD_FIELD = "cmd"
  private val CMD_CREATE = "create"
  private val CMD_READ = "read"
  private val CMD_UPDATE = "update"
  private val CMD_DELETE = "delete"
  private val COMMIT_LOG_FILE = "commitLog.txt"
  private val DELETED = "deleted"

  private val dataMap = Map.empty[String, String]
  private val dumpSize = context.system.settings.config.getString("storage.dump_size").toInt

  if (new File(COMMIT_LOG_FILE).exists) {
    readCommitLog()
  }

  var commitLog = new FileWriter(COMMIT_LOG_FILE, true/*append == true*/)
  log.debug("commit log is at " + new File(COMMIT_LOG_FILE).getAbsolutePath)

  def receive: Actor.Receive = {
    case msg => processMessage(msg, true)
  }

  private def processMessage(msg: Any, writeLog: Boolean) = msg match {
    case msg => {
      try {
        if (dataMap.size >= dumpSize) dumpToDisk()
        if (writeLog) logCommand(msg)

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
            if (dataMap.contains(name) || findOnDisk(name)) {//findOnDisk only if contains() return false
              if (DELETED == dataMap(name)) {
                dataMap(name) = o.optString(PERSON_PHONE)
                MESSAGE_CMD_OK
              } else
                MESSAGE_CMD_DUPLICATE
            } else {
              dataMap += (name -> o.optString(PERSON_PHONE))
              MESSAGE_CMD_OK
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
            if (dataMap.contains(name) || findOnDisk(name)) {
              val value = dataMap(name)
              if (!value.equals(DELETED)) value
              else MESSAGE_NOT_FOUND
            }
            else MESSAGE_NOT_FOUND
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
            dataMap += (name -> DELETED)
            MESSAGE_CMD_OK
          }
          case None => MESSAGE_MISSING_NAME
        }
      }
      case None => MESSAGE_NOT_FOUND
    }
  }

  private def readCommitLog() = {
    log.debug("Reading commit log")
    var reader: BufferedReader = null
    try {
      reader = new BufferedReader(new FileReader(COMMIT_LOG_FILE))
      Iterator.continually(reader.readLine).takeWhile(_ != null).foreach(msg => processMessage(msg, false))
    } catch {
      case e: IOException => log.debug("Error reading commitLog: " + e.getMessage)
    } finally {
       reader.close()
    }
    log.debug("Done reading commit log")
  }

  private def logCommand(msg: Any) = {
    try {
      commitLog.write(msg + "\n")
      commitLog.flush()
    } catch {
      case e: IOException => log.debug("Error writing to commit log: " + e.getMessage)
    }
  }

  private def dumpToDisk() = {
    //create a block of date
    val personList = for ((key, value) <- dataMap) yield {
      val personBuilder = Person.newBuilder()
      personBuilder.setName(key)
      personBuilder.setPhone(value)
      val person = personBuilder.build()
      person
    }

    val storage = model.PersonCard.Storage.newBuilder()
    val dataBlock = storage.addAllPerson(personList).build()

    //dump it
    var os: FileOutputStream = null
    try {
      val filename = path + new Date().getTime()
      os = new FileOutputStream(filename , false)
      dataBlock.writeTo(os)
      log.debug("DataBlock of size " + personList.size + " dumped to " + filename)
    } catch {
      case e: IOException => "IO exception"
    } finally {
      os.close()
    }

    //clean map
    dataMap.clear()

    //and delete commit log
    commitLog.close()
    new File(COMMIT_LOG_FILE).delete()
    commitLog = new FileWriter(COMMIT_LOG_FILE, false/*append == false*/)
  }

  private def findOnDisk(key: String) : Boolean = {
    //searching files from most recent to oldest
    for (file <- new java.io.File(path).listFiles.filter(_.getName.matches("\\d+")).sortWith(_.getName > _.getName)) {
      log.debug("searching in file " + file + " for key " + key)

      val storageBuilder = model.PersonCard.Storage.newBuilder()
      var in: DataInputStream = null

      try {
        in = new DataInputStream(new FileInputStream(file))
        val storage = storageBuilder.mergeFrom(in).build()
        storage.getPersonList.find(p => p.getName.equals(key)) match {
          case Some(person) => {
            person.getPhone match {
              case DELETED => {log.debug("Was deleted"); return false}
              case _ => {
                //Store value in memory, as it is
                dataMap += (person.getName -> person.getPhone)
                log.debug("Key {} found in file {}", key, file)
                return true
              }
            }
          }
          case None =>
        }
      } catch {
        case e: IOException => log.debug("Error reading datablock: " + e.getMessage)
      } finally {
        in.close()
      }
    }
    log.debug("Not found")
    false
  }

  override def postStop() = {
    commitLog.close()
  }
}
