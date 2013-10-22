package utils

import java.io._
import java.util.Date
import model.PersonCard.Person
import scala.collection.JavaConversions.{asJavaCollection=>_,_}
import com.google.protobuf.CodedInputStream
import scala.collection.mutable.{Set, Map}
import storage.{Lock, Slave}
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object CompactTool {
  val COMPACT_LOCK = "compact_metadata"
  val REPLACEMENT_LOCK = "replacement"
  val log = LoggerFactory.getLogger(CompactTool.getClass)

  private val dumpSize = Runtime.getRuntime().maxMemory() / 8 //Magic value, figured out during testing
  //Dump big snapshots to disk by portions of batchSize, so we do not store much data in RAM during snapshot creation
  private val batchSize = dumpSize / 4

  def compactSnapshots(path: String): Boolean = {
    if (! new File(path).exists() || ! new File(path).isDirectory) {
      log.debug(s"Dir $path not exists")
      return false
    }

    if (Lock.isLocked(path, Slave.DUMP_LOG_NAME)) {
      log.debug(s"Somebody is dumping data to $path (lock-file ${Slave.DUMP_LOG_NAME} exists)")
      return false
    }

    if (Lock.isLocked(path, COMPACT_LOCK) || Lock.isLocked(path, REPLACEMENT_LOCK)) {
      log.debug(s"Somebody is already compacting data (lock-file ${COMPACT_LOCK} or ${REPLACEMENT_LOCK} exists)")
      return false
    }
    log.debug("Compaction for dir {} started", path)
    //searching files from most recent to oldest
    val startTime = System.currentTimeMillis()
    val visitedKeys = Set[String]()
    val compactedDatabase = Map[String, String]()
    val listOfCompactedFiles = new java.io.File(path).listFiles.filter(_.getName.matches("\\d+")).filter(_.length() < dumpSize.toInt * 2).sortWith(_.getName > _.getName)

    if (listOfCompactedFiles.size <= 1) {
      log.debug("Nothing to compact. Abort.")
      return true
    }
    //COMPACT_LOCK stores files that are compacted. Later we can delete these files in replacement phase
    val lockWriter = new BufferedWriter(new FileWriter(new File(path, COMPACT_LOCK + ".lock")))
    listOfCompactedFiles.foreach(file => {lockWriter.write(file.getAbsolutePath); lockWriter.newLine(); log.debug("File {} will be compacted", file.getAbsolutePath)})
    lockWriter.close()

    var os: FileOutputStream = null
    var currentName = getFileName(path, new Date().getTime())
    var compactFile = new File(path, currentName.toString + ".tmp")

    try {
      os = new FileOutputStream(compactFile, false)
      log.debug("Start compaction in file " + compactFile.getAbsolutePath)
      var compactedDatabaseSize = 0
      var alreadyDumpedSize = 0
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
                  DumpTool.dumpDataBlockToDisk(compactedDatabase, os)
                  compactedDatabase.clear()
                  alreadyDumpedSize += compactedDatabaseSize
                  compactedDatabaseSize = 0
                }
                if (alreadyDumpedSize > dumpSize.toInt * 2 - 1) {
                  os.close()
                  currentName = getFileName(path, currentName)
                  compactFile = new File(path, currentName.toString + ".tmp")
                  log.debug("Dumping to another file (compact file exceeds max size of {}): {}", (dumpSize.toInt * 2 - 1), compactFile.getAbsolutePath)
                  os = new FileOutputStream(compactFile, false)
                  alreadyDumpedSize = 0
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
      DumpTool.dumpDataBlockToDisk(compactedDatabase, os)

    } catch {
      case e: IOException => log.debug("IO exception")
    } finally {
      os.close()
    }
    //compaction is successful, we can delete old snapshots

    Lock.createLock(path, REPLACEMENT_LOCK)
    val lockReader = new BufferedReader(new FileReader(new File(path, COMPACT_LOCK + ".lock")))
    Iterator.continually(lockReader.readLine()).takeWhile(_ != null).
      foreach(file => log.debug("Deletion result for file " + file + " is " + new File(file).delete))
    lockReader.close()

    new java.io.File(path).listFiles.filter(_.getName.matches("\\d+\\.tmp")).foreach(file => {
      log.debug("Moving " + file.getAbsolutePath + " to " + file.getAbsolutePath.dropRight(4))
      file.renameTo(new File(file.getAbsolutePath.dropRight(4)))
    })

    Lock.removeLock(path, COMPACT_LOCK)
    Lock.removeLock(path, REPLACEMENT_LOCK)
    log.debug("Data compacted in " + (System.currentTimeMillis() - startTime) + "ms")

    true
  }

  //starts from current time and searches earlier (name (== timestamp) is less than initName) non-occupied filename
  private def getFileName(path: String, seed: Long): Long = {
    var init = seed
    var filename: String = null
    do {
      filename = new File(path, init.toString).toString
      init -= 1
    } while (new File(filename).exists())
    init
  }
}
