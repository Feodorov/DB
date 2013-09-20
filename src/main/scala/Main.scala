import akka.actor.ActorSystem

object Main extends App {
  if (args.size != 3) {
    Console.println(arguments)
    System.exit(1)
  }
  Console.println(usage)
  val dbPath = args(0)
  val tcpPort = Integer.parseInt(args(1))
  val httpPort = Integer.parseInt(args(2))
  val system = ActorSystem("DB")

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


