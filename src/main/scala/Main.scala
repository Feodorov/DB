import akka.actor.{Props, ActorSystem}
import com.typesafe.config.ConfigFactory
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import listeners.{HttpListener, TcpListener, TerminalListener}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext
import storage.{Master, Slave, Client, Messages, Lock}
import utils.CompactTool

object Main extends App {
  if (args.size > 2) {
    Console.println(arguments)
    System.exit(1)
  }
  Console.println(usage)

  val role = args(0)
  val conf = ConfigFactory.load()

  role match {
    case "client" => {
      if (!Lock.createLock("client")) {
        Console.println(Messages.ALREADY_RUNNING)
        System.exit(2)
      }

      Runtime.getRuntime().addShutdownHook(new Thread() {
        override def run() = {println("Removing lock client"); Lock.removeLock("client")}
      })

      val actorSystem = ActorSystem("DB", conf.getConfig("client"))
      val tcpEndpoint = new InetSocketAddress("localhost", conf.getInt("client.tcp_port"))
      val terminalListener = actorSystem.actorOf(Props[TerminalListener], "terminal-listener")

      actorSystem.actorOf(TcpListener.props(tcpEndpoint), "tcp-listener")
      actorSystem.actorOf(HttpListener.props("localhost", conf.getInt("client.http_port")), "http-listener")
      actorSystem.actorOf(Props[Client], "storage-client")

      Iterator.continually(Console.readLine).filter(_ != null).takeWhile(_ != "shutdown").foreach(line => terminalListener ! line)

      terminalListener ! "shutdown"
      actorSystem.awaitTermination()
    }

    case "slave" => {
      val shardNumber = if (2 == args.size) Integer.parseInt(args(1)) else 0
      val config = conf.getObjectList("storage.instances").get(shardNumber).toConfig
      val name = config.getString("name")
      val path = config.getString("path")

      if (!Lock.createLock(name)) {
        Console.println(Messages.ALREADY_RUNNING)
        System.exit(2)
      }

      if (! new File(path).exists()) {
        Console.println(s"Slave dir $path doesn't exists")
        System.exit(3)
      }
      Runtime.getRuntime().addShutdownHook(new Thread() {
        override def run() = {println("Removing lock " + name); Lock.removeLock(name)}
      })
      val actorSystem = ActorSystem("DB", config)
      actorSystem.actorOf(Slave.props(path, config.getInt("max_files_on_disk")), name)
      import ExecutionContext.Implicits.global
      actorSystem.scheduler.schedule(Duration.create(0, TimeUnit.MILLISECONDS),
        Duration.create(config.getInt("compact_period"), TimeUnit.MILLISECONDS),
        new Runnable { override def run = CompactTool.compactSnapshots(path)})
      actorSystem.awaitTermination()
    }

    case "master" => {
      val config = conf.getConfig("master")
      val name = config.getString("name")
      val path = config.getString("path")

      Runtime.getRuntime().addShutdownHook(new Thread() {
        override def run() = {println("Removing lock " + name); Lock.removeLock(name)}
      })

      if (!Lock.createLock(name)) {
        Console.println(Messages.ALREADY_RUNNING)
        System.exit(2)
      }

      if (! new File(path).exists()) {
        Console.println(s"Master dir $path doesn't exists")
        System.exit(3)
      }

      val actorSystem = ActorSystem("DB", config)
      actorSystem.actorOf(Master.props(path, config.getInt("max_files_on_disk")), name)
      import ExecutionContext.Implicits.global
      actorSystem.scheduler.schedule(Duration.create(0, TimeUnit.MILLISECONDS),
        Duration.create(config.getInt("compact_period"), TimeUnit.MILLISECONDS),
        new Runnable { override def run = CompactTool.compactSnapshots(path)})
      actorSystem.awaitTermination()
    }

    case "compact" => {
      if (2 != args.size) {
        println("not enough arguments. Path to data dir needed.")
        System.exit(4)
      }
      println("Start compacting...")
      CompactTool.compactSnapshots(args(1))
      println("Done. Details in logfile")
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


