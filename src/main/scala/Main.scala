import akka.actor.ActorSystem
import java.io.File

object Main extends App {
  if (args.size != 3) {
    Console.println(arguments)
    System.exit(1)
  }
  val dbPath = args(0)
  if (!new File(dbPath).exists() || !new File(dbPath + "shard1/").exists() || !new File(dbPath + "shard2/").exists()) {
    Console.println("""Either path to DB not exists, or dirs "shard1/" or "shard2/" inside it doesn't exist """)
    System.exit(2)
  }

  val tcpPort = Integer.parseInt(args(1))
  val httpPort = Integer.parseInt(args(2))
  val system = ActorSystem("DB")

  Console.println(usage)
  val master = system.actorOf(Master.props(dbPath, tcpPort, httpPort), "master")
  Iterator.continually(Console.readLine).filter(_ != null).takeWhile(_ != "shutdown").foreach(line => master ! line)
  master ! "shutdown"
  system.awaitTermination()
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
  "/PATH/TO/BD/WITH/SLASH/AT/THE/END/ TCP_PORT HTTP_PORT"
}


