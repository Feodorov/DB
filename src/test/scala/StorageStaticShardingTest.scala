import akka.actor.{PoisonPill, Props, ActorSystem}
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import java.io.File
import org.scalatest.matchers.MustMatchers
import org.scalatest.{BeforeAndAfterAll, WordSpec}
import storage.{StorageStaticShardingClient, Messages, Storage}

/**
 * Created with IntelliJ IDEA.
 * User: Kos
 * Date: 07.10.13
 * Time: 9:28
 * To change this template use File | Settings | File Templates.
 */
class StorageStaticShardingTest extends TestKit(ActorSystem("StorageStaticShardingTest"))
  with ImplicitSender with WordSpec with BeforeAndAfterAll with MustMatchers {

  val DIR = "./test_storage/"
  var storageShard1ActorRef: TestActorRef[Storage] = null
  var storageShard2ActorRef: TestActorRef[Storage] = null
  var storageClientActorRef: TestActorRef[Storage] = null

  override def beforeAll() {
    var commitLog = new File("storage-shard1commitLog.txt")
    if (commitLog.exists()) {
      commitLog.delete()
    }

    commitLog = new File("storage-shard2commitLog.txt")
    if (commitLog.exists()) {
      commitLog.delete()
    }

    val storageDir = new File(DIR)
    deleteDir(storageDir)
    storageDir.mkdir()
    new File(DIR + "shard1/").mkdir()
    new File(DIR + "shard2/").mkdir()

    storageShard1ActorRef = TestActorRef(Props(new Storage(DIR)), name = "storage-shard1")
    storageShard2ActorRef = TestActorRef(Props(new Storage(DIR)), name = "storage-shard2")
    storageClientActorRef = TestActorRef(StorageStaticShardingClient.props("/user"), "storage-client")
  }

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
    deleteDir(new File(DIR))
    new File("storage-shard1commitLog.txt").delete()
    new File("storage-shard2commitLog.txt").delete()
  }

  "storage (simple operations) " should {

    "support create()" in {
      storageClientActorRef ! "{\"cmd\":\"create\", \"person\":{\"name\":\"zkos\",\"phone\":\"123\"}}"
      expectMsg(Messages.MESSAGE_CMD_OK)
      storageClientActorRef ! "{\"cmd\":\"create\", \"person\":{\"name\":\"akos\",\"phone\":\"456\"}}"
      expectMsg(Messages.MESSAGE_CMD_OK)
    }

    "disallow creation of duplicated records" in {
      storageClientActorRef ! "{\"cmd\":\"create\", \"person\":{\"name\":\"akos\",\"phone\":\"456\"}}"
      expectMsg(Messages.MESSAGE_CMD_DUPLICATE)
      storageClientActorRef ! "{\"cmd\":\"create\", \"person\":{\"name\":\"zkos\",\"phone\":\"123\"}}"
      expectMsg(Messages.MESSAGE_CMD_DUPLICATE)
    }

    "support read()" in {
      storageClientActorRef ! "{\"cmd\":\"read\", \"person\":{\"name\":\"akos\"}}"
      expectMsg("456")

      storageClientActorRef ! "{\"cmd\":\"read\", \"person\":{\"name\":\"zkos\"}}"
      expectMsg("123")
    }

    "support update()" in {
      storageClientActorRef ! "{\"cmd\":\"update\", \"person\":{\"name\":\"akos\",\"phone\":\"789\"}}"
      expectMsg(Messages.MESSAGE_CMD_OK)

      storageClientActorRef ! "{\"cmd\":\"update\", \"person\":{\"name\":\"zkos\",\"phone\":\"987\"}}"
      expectMsg(Messages.MESSAGE_CMD_OK)

      storageClientActorRef ! "{\"cmd\":\"read\", \"person\":{\"name\":\"akos\"}}"
      expectMsg("789")

      storageClientActorRef ! "{\"cmd\":\"read\", \"person\":{\"name\":\"zkos\"}}"
      expectMsg("987")
    }

    "support delete()" in {
      storageClientActorRef ! "{\"cmd\":\"delete\", \"person\":{\"name\":\"akos\"}}"
      expectMsg(Messages.MESSAGE_CMD_OK)
      storageClientActorRef ! "{\"cmd\":\"read\", \"person\":{\"name\":\"akos\"}}"
      expectMsg(Messages.MESSAGE_NOT_FOUND)

      storageClientActorRef ! "{\"cmd\":\"delete\", \"person\":{\"name\":\"zkos\"}}"
      expectMsg(Messages.MESSAGE_CMD_OK)
      storageClientActorRef ! "{\"cmd\":\"read\", \"person\":{\"name\":\"zkos\"}}"
      expectMsg(Messages.MESSAGE_NOT_FOUND)
    }

    "restore state from commitLog after crash" in {
      storageClientActorRef ! "{\"cmd\":\"create\", \"person\":{\"name\":\"acrash_test\",\"phone\":\"666\"}}"
      expectMsg(Messages.MESSAGE_CMD_OK)
      storageClientActorRef ! "{\"cmd\":\"create\", \"person\":{\"name\":\"zcrash_test\",\"phone\":\"777\"}}"
      expectMsg(Messages.MESSAGE_CMD_OK)

      storageShard1ActorRef ! PoisonPill
      storageShard2ActorRef ! PoisonPill
      storageShard1ActorRef = TestActorRef(Props(new Storage(DIR)), name = "storage-shard1")
      storageShard2ActorRef = TestActorRef(Props(new Storage(DIR)), name = "storage-shard2")

      storageClientActorRef ! "{\"cmd\":\"read\", \"person\":{\"name\":\"acrash_test\"}}"
      expectMsg("666")
      storageClientActorRef ! "{\"cmd\":\"read\", \"person\":{\"name\":\"zcrash_test\"}}"
      expectMsg("777")
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
