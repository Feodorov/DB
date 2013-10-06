import sys.process._
import akka.actor.{Props, ActorSystem}
import akka.testkit.{TestActorRef, ImplicitSender, TestKit}
import org.scalatest.matchers.MustMatchers
import org.scalatest.{BeforeAndAfterAll, WordSpec}

/**
 * Created with IntelliJ IDEA.
 * User: Kos
 * Date: 06.10.13
 * Time: 10:57
 * To change this template use File | Settings | File Templates.
 */
class StorageTest extends TestKit(ActorSystem("StorageTest"))
  with ImplicitSender with WordSpec with BeforeAndAfterAll with MustMatchers {

  override def beforeAll() {
    "mkdir test_storage".!
  }

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
    "rm -rf test_storage".!
  }

  "Storage" should {
    val storageActorRef = TestActorRef(Props(new Storage("./test_storage")))

    "support create()" in {
      storageActorRef ! "{\"cmd\":\"create\", \"person\":{\"name\":\"kos\",\"phone\":\"123\"}}"
      expectMsg("Card kos saved successfully")
    }

    "support read()" in {
      storageActorRef ! "{\"cmd\":\"read\", \"person\":{\"name\":\"kos\"}}"
      expectMsg("Card found: name: \"kos\"\nphone: \"123\"\n")
    }

    "support update()" in {
      storageActorRef ! "{\"cmd\":\"update\", \"person\":{\"name\":\"kos\",\"phone\":\"456\"}}"
      expectMsg("Card kos saved successfully")
    }

    "support delete()" in {
      storageActorRef ! "{\"cmd\":\"delete\", \"person\":{\"name\":\"kos\"}}"
      expectMsg("Success")
    }
  }
}
