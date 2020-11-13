package Actors
import Actors.ServerActor.{createHashedNodeId, initializeFingerTable}
import akka.actor._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

class SupervisorActor(system: ActorSystem, numNodes: Int) extends Actor with LazyLogging {
  //val config: Config = ConfigFactory.load()

  override def receive: Receive = {
    case createChordNodes => {
      val random = scala.util.Random
      //ActorRef - Reference that points to the actor instance
      val actorNodes = new Array[ActorRef](numNodes)
      for (x <- 0 until numNodes){
        var nodeId = random.nextInt(Integer.MAX_VALUE)
        actorNodes(x) = system.actorOf(Props[ServerActor], name = "Node" + x + "-in-chord-ring")
        actorNodes(x) ! createHashedNodeId(nodeId)
        actorNodes(x) ! initializeFingerTable()
      }
      logger.info("Chord nodes created.")
    }
  }
}