import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import java.io.{InputStreamReader, BufferedReader, File}
import org.scalatest.matchers.MustMatchers
import org.scalatest.{BeforeAndAfterAll, WordSpec}
import scala.concurrent.duration._
import storage.{Messages, Storage}

/**
 * Created with IntelliJ IDEA.
 * User: Kos
 * Date: 06.10.13
 * Time: 17:12
 * To change this template use File | Settings | File Templates.
 */
class StorageBulkTest extends TestKit(ActorSystem("StorageBulkTest"))
with ImplicitSender with WordSpec with BeforeAndAfterAll with MustMatchers {
  val DIR = "./test_storage/"
  var storageActorRef: TestActorRef[Storage] = null

  override def beforeAll() {
    val commitLog = new File("./storagecommitLog.txt")
    if (commitLog.exists()) {
      commitLog.delete()
    }

    val storageDir = new File(DIR)
    deleteDir(storageDir)
    storageDir.mkdir()

    storageActorRef = TestActorRef(Props(new Storage(DIR, 25)), name = "storage")
  }

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
    deleteDir(new File(DIR))
    new File("storagecommitLog.txt").delete()
  }

  "storage (stress tests)" should {
    "not find deleted keys in earlier dumps" in {
      val capacity = 401
      for(i <- 1 to capacity) {
        storageActorRef ! "{\"cmd\":\"create\", \"person\":{\"name\":\"kos#" + i + "#\",\"phone\":\"" + i + "\"}}"
        expectMsg(Messages.MESSAGE_CMD_OK)
      }

      for(i <- 1 to capacity) {
        storageActorRef ! "{\"cmd\":\"delete\", \"person\":{\"name\":\"kos#" + i + "#\"}}"
        expectMsg(Messages.MESSAGE_CMD_OK)
      }

      for(i <- 1 to capacity) {
        storageActorRef ! "{\"cmd\":\"read\", \"person\":{\"name\":\"kos#" + i + "#\"}}"
        expectMsg(Messages.MESSAGE_NOT_FOUND)
      }
    }

    "support more than 4Gb of data" in {
      val reader = new BufferedReader(new InputStreamReader(this.getClass.getResourceAsStream("war_and_peace.txt")))
      val sb = new StringBuilder
      Iterator.continually(reader.readLine).takeWhile(_ != null).foreach(line => sb.append(line))
      val bigData = sb.toString //2.4Mb string

      val capacity = 800
      for(i <- 1 to capacity) {
        storageActorRef ! "{\"cmd\":\"create\", \"person\":{\"name\":\"war_and_peace#" + i + "#\",\"phone\":\"" + i + bigData + "\"}}"
        expectMsg(Messages.MESSAGE_CMD_OK)
        storageActorRef ! "{\"cmd\":\"read\", \"person\":{\"name\":\"war_and_peace#" + i + "#\"}}"
        expectMsg(i + bigData)

        if (0 == i % 100) Console.println(i + " entries of " + capacity + " tested")
      }
    }

    "support 1M of 1kb data" in {
      val sb = new StringBuilder
      for (i <- 1 to 250) sb.append("test")
      val bigData = sb.toString //1kb string

      val capacity = 1000000
      for(i <- 1 to capacity) {
        if (0 == i % 100000) Console.println(i + " entries of " + capacity + " tested")
        storageActorRef ! "{\"cmd\":\"create\", \"person\":{\"name\":\"small_key#" + i + "#\",\"phone\":\"" + i + bigData + "\"}}"
        expectMsg(max = new FiniteDuration(10, SECONDS), Messages.MESSAGE_CMD_OK)

        storageActorRef ! "{\"cmd\":\"read\", \"person\":{\"name\":\"small_key#" + i + "#\"}}"
        expectMsg(max = new FiniteDuration(10, SECONDS), i + bigData)
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
