import akka.actor.{Props, ActorSystem}
import com.typesafe.config.ConfigFactory
import java.net.InetSocketAddress
import listeners.{HttpListener, TcpListener, TerminalListener}
import storage.{Storage, StorageStaticShardingClient}

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
      val actorSystem = ActorSystem("DB", conf.getConfig("client"))

      val tcpEndpoint = new InetSocketAddress("localhost", conf.getInt("client.tcp_port"))
      val terminalListener = actorSystem.actorOf(Props[TerminalListener], "terminal-listener")
      actorSystem.actorOf(TcpListener.props(tcpEndpoint), "tcp-listener")
      actorSystem.actorOf(HttpListener.props("localhost", conf.getInt("client.http_port")), "http-listener")
      actorSystem.actorOf(Props[StorageStaticShardingClient], "storage-client")
      Iterator.continually(Console.readLine).filter(_ != null).takeWhile(_ != "shutdown").foreach(line => terminalListener ! line)
      terminalListener ! "shutdown"
      actorSystem.awaitTermination()
    }

    case "storage" => {
      val config = conf.getObjectList("storage.instances").get(shardNumber).toConfig
      val actorSystem = ActorSystem("DB", config)

      actorSystem.actorOf(Storage.props(config.getString("path"), config.getInt("max_files_on_disk")), config.getString("name"))
      actorSystem.awaitTermination()
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
  "{client|storage} [shard#]"
}


