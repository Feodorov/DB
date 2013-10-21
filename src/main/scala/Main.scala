import akka.actor.{Props, ActorSystem}
import com.typesafe.config.ConfigFactory
import java.io.{File, FileNotFoundException}
import java.net.InetSocketAddress
import listeners.{HttpListener, TcpListener, TerminalListener}
import storage.{MasterStorage, Storage, StorageStaticShardingClient, Messages, Lock}

object Main extends App {
  if (args.size > 2) {
    Console.println(arguments)
    System.exit(1)
  }
  Console.println(usage)

  val role = args(0)
  val shardNumber = if (2 == args.size) Integer.parseInt(args(1)) else 0
  val conf = ConfigFactory.load()

  role match {
    case "client" => {
      if (!Lock.createLock("client")) {
        Console.println(Messages.ALREADY_RUNNING)
        System.exit(2)
      }

      val actorSystem = ActorSystem("DB", conf.getConfig("client"))
      val tcpEndpoint = new InetSocketAddress("localhost", conf.getInt("client.tcp_port"))
      val terminalListener = actorSystem.actorOf(Props[TerminalListener], "terminal-listener")

      actorSystem.actorOf(TcpListener.props(tcpEndpoint), "tcp-listener")
      actorSystem.actorOf(HttpListener.props("localhost", conf.getInt("client.http_port")), "http-listener")
      actorSystem.actorOf(Props[StorageStaticShardingClient], "storage-client")

      Iterator.continually(Console.readLine).filter(_ != null).takeWhile(_ != "shutdown").foreach(line => terminalListener ! line)

      terminalListener ! "shutdown"
      actorSystem.awaitTermination()
      Lock.removeLock("client")
    }

    case "slave" => {
      val config = conf.getObjectList("storage.instances").get(shardNumber).toConfig
      val name = config.getString("name")
      val path = config.getString("path")

      if (!Lock.createLock(name)) {
        Console.println(Messages.ALREADY_RUNNING)
        System.exit(2)
      }

      if (! new File(path).exists()) {
        Console.println(s"Storage dir $path doesn't exists")
        System.exit(3)
      }

      val actorSystem = ActorSystem("DB", config)
      actorSystem.actorOf(Storage.props(path, config.getInt("max_files_on_disk")), name)
      actorSystem.awaitTermination()
      Lock.removeLock(name)
    }

    case "master" => {
      val config = conf.getConfig("master")
      val name = config.getString("name")
      val path = config.getString("path")


      if (!Lock.createLock(name)) {
        Console.println(Messages.ALREADY_RUNNING)
        System.exit(2)
      }

      if (! new File(path).exists()) {
        Console.println(s"Storage dir $path doesn't exists")
        System.exit(3)
      }

      val actorSystem = ActorSystem("DB", config)
      actorSystem.actorOf(MasterStorage.props(path, config.getInt("max_files_on_disk")), name)
      actorSystem.awaitTermination()
      Lock.removeLock(name)
    }

    case _ => Console.println(arguments); System.exit(2)
  }

  Console.println("Bye!")

  private def usage(): String = "Hi!\n"+
    "Server supports 4 major commands: create, read, update, delete.\n" +
    "All commands are accepted in JSON format. To create record simply print this:\n" +
    "{\"cmd\":\"create\", \"person\":{\"name\":\"kos\",\"phone\":\"123\"}}\n" +
    "To read record: {\"cmd\":\"read\", \"person\":{\"name\":\"kos\"}}\n" +
    "To update record: {\"cmd\":\"update\", \"person\":{\"name\":\"kos\",\"phone\":\"456\"}}\n" +
    "To delete record: {\"cmd\":\"delete\", \"person\":{\"name\":\"kos\"}}\n" +
    "To shutdown server simply print \"shutdown\" to the console."

  private def arguments(): String = "Missing arguments: " +
  "{client|master|slave} [shard#]"
}


