package Actors
import akka.actor._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

class SupervisorActor(system: ActorSystem, numNodes: Int) extends Actor with LazyLogging {
  val config: Config = ConfigFactory.load()
  val numComputersInChordRing: Int = config.getInt("count.numComputers")

  override def receive: Receive = {
    case createChordNodes => {
      val random = scala.util.Random
      //ActorRef - Reference that points to the actor instance
      val actorNodes = new Array[ActorRef](numNodes)
      for (x <- 0 until numNodes){
        var nodeId = random.nextInt(Integer.MAX_VALUE)
        actorNodes(x) = system.actorOf(Props(new ServerActor(nodeId)), name = "Node" + x + "-in-chord-ring")
        actorNodes(x) ! "default"
      }
      println("Chord nodes created.")
    }
  }
}