import java.io.File
import akka.actor.{PoisonPill, Props, ActorSystem}
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import org.scalatest.matchers.MustMatchers
import org.scalatest.{BeforeAndAfterAll, WordSpec}
import storage.{Messages, Storage}

/**
 * Created with IntelliJ IDEA.
 * User: Kos
 * Date: 06.10.13
 * Time: 10:57
 * To change this template use File | Settings | File Templates.
 */
class StorageSimpleTest extends TestKit(ActorSystem("StorageSimpleTest"))
  with ImplicitSender with WordSpec with BeforeAndAfterAll with MustMatchers {

  val DIR = "./test_storage/"
  var storageActorRef: TestActorRef[Storage] = null

  override def beforeAll() {
    val commitLog = new File("storagecommitLog.txt")
    if (commitLog.exists()) {
      commitLog.delete()
    }

    val storageDir = new File(DIR)
    deleteDir(storageDir)
    storageDir.mkdir()

    storageActorRef = TestActorRef(Props(new Storage(DIR)), name = "storage")
  }

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
    deleteDir(new File(DIR))
    new File("storagecommitLog.txt").delete()
  }

  "storage (simple operations) " should {

    "support create()" in {
      storageActorRef ! "{\"cmd\":\"create\", \"person\":{\"name\":\"kos\",\"phone\":\"123\"}}"
      expectMsg(Messages.MESSAGE_CMD_OK)
    }

    "disallow creation of duplicated records" in {
      storageActorRef ! "{\"cmd\":\"create\", \"person\":{\"name\":\"kos\",\"phone\":\"123\"}}"
      expectMsg(Messages.MESSAGE_CMD_DUPLICATE)
    }

    "support read()" in {
      storageActorRef ! "{\"cmd\":\"read\", \"person\":{\"name\":\"kos\"}}"
      expectMsg("123")
    }

    "support update()" in {
      storageActorRef ! "{\"cmd\":\"update\", \"person\":{\"name\":\"kos\",\"phone\":\"456\"}}"
      expectMsg(Messages.MESSAGE_CMD_OK)
    }

    "support delete()" in {
      storageActorRef ! "{\"cmd\":\"delete\", \"person\":{\"name\":\"kos\"}}"
      expectMsg(Messages.MESSAGE_CMD_OK)
      storageActorRef ! "{\"cmd\":\"read\", \"person\":{\"name\":\"kos\"}}"
      expectMsg(Messages.MESSAGE_NOT_FOUND)
    }

    "restore state from commitLog after crash" in {
      storageActorRef ! "{\"cmd\":\"create\", \"person\":{\"name\":\"crash_test\",\"phone\":\"666\"}}"
      expectMsg(Messages.MESSAGE_CMD_OK)
      storageActorRef ! PoisonPill
      storageActorRef = TestActorRef(Props(new Storage(DIR)), name = "storage")
      storageActorRef ! "{\"cmd\":\"read\", \"person\":{\"name\":\"crash_test\"}}"
      expectMsg("666")
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
