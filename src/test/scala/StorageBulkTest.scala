import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import java.io.{InputStreamReader, BufferedReader, File}
import org.scalatest.matchers.MustMatchers
import org.scalatest.{BeforeAndAfterAll, WordSpec}

/**
 * Created with IntelliJ IDEA.
 * User: Kos
 * Date: 06.10.13
 * Time: 17:12
 * To change this template use File | Settings | File Templates.
 */
class StorageBulkTest extends TestKit(ActorSystem("StorageTest"))
with ImplicitSender with WordSpec with BeforeAndAfterAll with MustMatchers {
  val DIR = "./test_storage/"
  var storageActorRef: TestActorRef[Storage] = null

  override def beforeAll() {
    val commitLog = new File("./commitLog.txt")
    if (commitLog.exists()) {
      commitLog.delete()
    }

    val storageDir = new File(DIR)
    deleteDir(storageDir)
    storageDir.mkdir()

    storageActorRef = TestActorRef(Props(new Storage(DIR)))
  }

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
//    deleteDir(new File(DIR))
//    new File("commitLog.txt").delete()
  }

  "Storage (stress tests)" should {
    "support filedump" in {
      val capacity = 20
      for(i <- 1 to capacity) {
        storageActorRef ! "{\"cmd\":\"create\", \"person\":{\"name\":\"kos#" + i + "#\",\"phone\":\"123" + i + "\"}}"
        expectMsg("Success")
      }

      for(i <- 1 to capacity) {
        storageActorRef ! "{\"cmd\":\"read\", \"person\":{\"name\":\"kos#" + i + "#\"}}"
        expectMsg("123" + i)
      }

      for(i <- 1 to capacity) {
        storageActorRef ! "{\"cmd\":\"create\", \"person\":{\"name\":\"kos#" + i + "#\",\"phone\":\"123" + i + "\"}}"
        expectMsg("Failed. Key already exists")
      }
    }

    "not find deleted keys in earlier dumps" in {
      val capacity = 20

      for(i <- 1 to capacity) {
        storageActorRef ! "{\"cmd\":\"delete\", \"person\":{\"name\":\"kos#" + i + "#\"}}"
        expectMsg("Success")
      }

      for(i <- 1 to capacity) {
        storageActorRef ! "{\"cmd\":\"read\", \"person\":{\"name\":\"kos#" + i + "#\"}}"
        expectMsg("Value not found")
      }
    }

    "support more than 4Gb of data" in {
      val reader = new BufferedReader(new InputStreamReader(this.getClass.getResourceAsStream("war_and_peace.txt")))
      val sb = new StringBuilder
      Iterator.continually(reader.readLine).takeWhile(_ != null).foreach(line => sb.append(line))
      val bigData = sb.toString //2.4Mb string

      val capacity = 200
      Console.println("Current tests uses up to 0.5Gb of data. If you want to run 4Gb test, change 'capacity' val above this message in code")
      for(i <- 1 to capacity) {
        storageActorRef ! "{\"cmd\":\"create\", \"person\":{\"name\":\"war_and_peace#" + i + "#\",\"phone\":\"" + i + bigData + "\"}}"
        expectMsg("Success")
        if (0 == i % 100) Console.println(i + " entries added")
      }

      for(i <- 1 to capacity) {
        storageActorRef ! "{\"cmd\":\"read\", \"person\":{\"name\":\"war_and_peace#" + i + "#\"}}"
        expectMsg(i + bigData)
        if (0 == i % 100) Console.println(i + " entries read")
      }
    }
  }

  private def deleteDir(dir: File): Boolean = {
    if (dir.exists()) {
      for (file <- dir.listFiles) {
        if (file.isDirectory)
          deleteDir(file)
        else {
          file.delete()
          true
        }
      }
      dir.delete()
    }

    false
  }
}
