package storage

import akka.actor.{Props, Actor, ActorLogging}
import java.io._
import java.util.Date
import model.PersonCard.Person
import org.json.{JSONObject, JSONException}
import scala.Some
import scala.collection.mutable._
import scala.collection.JavaConversions.{asJavaCollection=>_,_}
import com.google.protobuf.CodedInputStream

/**
 * Date: 16.09.13
 * Time: 23:00
 * kfeodorov@yandex-team.ru
 */

object Storage{
  def props(path: String, maxFiles: Int): Props = Props(new Storage(path, maxFiles))
}

class Storage(path: String, maxFiles: Int) extends Actor with ActorLogging {
  private val COMMIT_LOG_FILE = self.path.name + "commitLog.txt"

  private val dataMap = Map.empty[String, String]
  //threshold. We need to compact snapshots if there are more than maxFilesOnDisk snapshots
  private val maxFilesOnDisk = maxFiles

  //max allowed total size of all values in dataMap RAM. Dump dataMap on disk if its size (var dataSize) is bigger than dumpSize (in bytes)
  private val dumpSize = Runtime.getRuntime().maxMemory() / 8 //Magic value, figured out during testing
  private var dataSize = 0

  //Dump big snapshots to disk by portions of batchSize, so we do not store much data in RAM during snapshot creation
  private val batchSize = dumpSize / 4

  //Just a speedup of check if key exists in create(). Otherwise we need to search in all snapshots on every create() call
  //Deleted keys are actually deleted from set (unlike map), so it doesn't occupy to much RAM (I hope)
  private val keySet = Set[String]()

  log.debug("Max heap is {} Mb", 4 * dumpSize / 1024 / 1024)
  if (new File(COMMIT_LOG_FILE).exists) {
    readCommitLog()
  }

  var commitLog = new FileWriter(COMMIT_LOG_FILE, true/*append == true*/)
  log.debug("commit log is at " + new File(COMMIT_LOG_FILE).getAbsolutePath)

  def receive: Actor.Receive = {
    case msg => {
      if (msg.toString.trim.equals("shutdown")) {
        log.debug("shutdown received")
        sender ! "OK"
        context.system.shutdown()
      } else {
        processMessage(msg, true)
      }
    }
  }

  protected def processMessage(msg: Any, writeLog: Boolean) = msg match {
    case msg => {
      try {
        if (dataSize >= dumpSize) {
          log.debug("Dumping data, because data size is {} bytes, and it is greater than" +
            " maximum allowed value {} (0.25 of max heap size)", dataSize, dumpSize)
          dumpData(dataMap, path + new Date().getTime())
          dataSize = 0
        }
        if (writeLog) logCommand(msg)

        val json = new JSONObject(msg.toString.trim)
        val person = Option(json.getJSONObject(Messages.PERSON_OBJECT))
        Option(json.getString(Messages.CMD_FIELD)) match {
          case Some(Messages.CMD_CREATE) => sender ! create(person)
          case Some(Messages.CMD_READ) => sender ! read(person)
          case Some(Messages.CMD_UPDATE) => sender ! update(person)
          case Some(Messages.CMD_DELETE) => sender ! delete(person)
          case Some(_) => sender ! Messages.MESSAGE_MISSING_CMD
          case None => Messages.MESSAGE_MISSING_CMD
        }
      } catch {
        case e: JSONException => sender ! "Parsing error. It is not a valid json"
      }
    }
  }
  private def create(o: Option[JSONObject]): String = {
    o match {
      case Some(o) => {
        Option(o.getString(Messages.PERSON_NAME)) match {
          case Some(name) => {
            if (keySet.contains(name)) {
                Messages.MESSAGE_CMD_DUPLICATE
            } else {
              val value = o.optString(Messages.PERSON_PHONE)
              dataMap += (name -> value)
              dataSize += value.size
              keySet += name
              Messages.MESSAGE_CMD_OK
            }
          }
          case None => Messages.MESSAGE_MISSING_NAME
        }
      }
      case None => Messages.MESSAGE_MISSING_DATA
    }
  }

  private def read(o: Option[JSONObject]): String = {
    o match {
      case Some(o) => {
        Option(o.getString(Messages.PERSON_NAME)) match {
          case Some(name) => {
            var found = dataMap.contains(name)
            if (! found) found = found || findOnDisk(name)
            if (found) {
              val value = dataMap(name)
              if (!value.equals(Messages.DELETED)) value
              else Messages.MESSAGE_NOT_FOUND
            }
            else Messages.MESSAGE_NOT_FOUND
          }
          case None => Messages.MESSAGE_MISSING_NAME
        }
      }
      case None => Messages.MESSAGE_NOT_FOUND
    }
  }

  private def update(o: Option[JSONObject]): String = {
    delete(o)
    create(o)
  }

  private def delete(o: Option[JSONObject]): String = {
    o match {
      case Some(o) => {
        Option(o.getString(Messages.PERSON_NAME)) match {
          case Some(name) => {
            dataMap += (name -> Messages.DELETED)
            dataSize += Messages.DELETED.size
            keySet -= name
            Messages.MESSAGE_CMD_OK
          }
          case None => Messages.MESSAGE_MISSING_NAME
        }
      }
      case None => Messages.MESSAGE_NOT_FOUND
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

  private def dumpData(map: Map[String, String], filename: String) = {
    var os: FileOutputStream = null
    try {
      os = new FileOutputStream(filename , false)
      dumpDataBlockToDisk(map, os)
    } catch {
      case e: IOException => "IO exception"
    } finally {
      os.close()
    }

    //compact files if needed
    if (new java.io.File(path).listFiles.size > maxFilesOnDisk) compactSnapshots()
    //clean map
    dataMap.clear()

    //and delete commit log
    commitLog.close()
    new File(COMMIT_LOG_FILE).delete()
    commitLog = new FileWriter(COMMIT_LOG_FILE, false/*append == false*/)
  }

  private def dumpDataBlockToDisk(map: Map[String, String], os: FileOutputStream) = {
    //create a block of date
    val personList = for ((key, value) <- map) yield {
      val personBuilder = Person.newBuilder()
      personBuilder.setName(key)
      personBuilder.setPhone(value)
      val person = personBuilder.build()
      person
    }

    val storage = model.PersonCard.Storage.newBuilder()
    val dataBlock = storage.addAllPerson(personList).build()

    //dump it
    dataBlock.writeTo(os)
    log.debug("DataBlock of " + personList.size + " pairs dumped")
  }

  private def compactSnapshots() = {
    //searching files from most recent to oldest
    val startTime = System.currentTimeMillis()
    val visitedKeys = Set[String]()
    val compactedDatabase = Map[String, String]()
    val listOfCompactedFiles = new java.io.File(path).listFiles.filter(_.getName.matches("\\d+")).sortWith(_.getName > _.getName)
    var os: FileOutputStream = null
    var filename: String = null
    do {
      filename = path + new Date().getTime()
    } while (new File(filename).exists())

    try {
      os = new FileOutputStream(filename, false)
      log.debug("Start compaction in file " + filename)
      var compactedDatabaseSize = 0
      for (file <- listOfCompactedFiles) {
        log.debug("compacting " + file)
        if (file.exists()) {
          val storageBuilder = model.PersonCard.Storage.newBuilder()
          var in: DataInputStream = null

          try {
            in = new DataInputStream(new FileInputStream(file))
            val input = CodedInputStream.newInstance(in)
            input.setSizeLimit(dumpSize.toInt * 2)
            val storage = storageBuilder.mergeFrom(input).build()
            for (person <- storage.getPersonList) {
              //store only the latest info from file with biggest timestamp
              //each file contains distinct keys (because it is a map dump) -> no need to worry about duplicates
              if (!visitedKeys.contains(person.getName)) {
                visitedKeys += person.getName
                compactedDatabaseSize += person.getPhone.size
                compactedDatabase += (person.getName -> person.getPhone)
                if (compactedDatabaseSize > batchSize) {
                  dumpDataBlockToDisk(compactedDatabase, os)
                  compactedDatabase.clear()
                  compactedDatabaseSize = 0
                }
              }
            }
          } catch {
            case e: IOException => log.debug("Error reading datablock while compacting: " + e.getMessage)
          } finally {
            in.close()
          }
        } else {
          log.debug("Strange, but file {} doesn't exists anymore", file.getAbsolutePath)
        }
      }
      dumpDataBlockToDisk(compactedDatabase, os)

    } catch {
      case e: IOException => "IO exception"
    } finally {
      os.close()
    }
    //compaction is successfull, we can delete old snapshots
    listOfCompactedFiles.foreach(file => log.debug("Deletion result for file " + file + " is " + file.delete))

    log.debug("Data compacted in " + (System.currentTimeMillis() - startTime) + "ms")
  }

  private def findOnDisk(key: String) : Boolean = {
    //searching files from most recent to oldest
    log.debug("searching in files for key " + key)
    val startTime = System.currentTimeMillis()
    for (file <- new java.io.File(path).listFiles.filter(_.getName.matches("\\d+")).sortWith(_.getName > _.getName)) {
//      log.debug("searching in file " + file + " for key " + key)
      if (file.exists()) {
        val storageBuilder = model.PersonCard.Storage.newBuilder()
        var in: DataInputStream = null

        try {
          in = new DataInputStream(new FileInputStream(file))
          val input = CodedInputStream.newInstance(in)
          input.setSizeLimit(dumpSize.toInt * 2)
          val storage = storageBuilder.mergeFrom(input).build()
          storage.getPersonList.find(p => p.getName.equals(key)) match {
            case Some(person) => {
              person.getPhone match {
                case Messages.DELETED => {log.debug("Was deleted. Done in " + (System.currentTimeMillis() - startTime) + "ms"); return false}
                case _ => {
                  //Store value in memory, as it is
                  dataMap += (person.getName -> person.getPhone)
                  dataSize += person.getPhone.size
//                  log.debug("Key {} found in file {}", key, file)
                  return true
                }
              }
            }
            case None =>
          }
        } catch {
          case e: IOException => log.debug("Error reading datablock while searching: " + e.getMessage)
        } finally {
          in.close()
        }
      } else {
        log.debug("Strange, but file {} doesn't exists anymore", file.getAbsolutePath)
      }
    }
    log.debug("Key " + key + " not found. Done in " + (System.currentTimeMillis() - startTime) + "ms")
    false
  }

  override def postStop() = {
    commitLog.close()
  }
}
