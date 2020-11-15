package Actors
import Actors.ServerActor.{initializeFingerTable, joinRing, updateHashedNodeId}
import Utils.Utility
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
      val firstNode  = Utility.sha1("n0")
      val initialNode = system.actorOf(Props(new ServerActor(firstNode)), name = "Node" + 0 + "-in-chord-ring")
      actorNodes(0) = initialNode
      actorNodes(0) ! updateHashedNodeId(firstNode)
      logger.info("First Node id => " + 0 + "\t\tHashedNodeId => " + firstNode)
      actorNodes(0) ! initializeFingerTable()
      for (x <- 1 until numNodes){
        //var nodeId = "n" + x
       // var nodeId = random.nextInt(Integer.MAX_VALUE)
        val nodeHash = Utility.sha1("n"+x)
        actorNodes(x) = system.actorOf(Props(new ServerActor(nodeHash)), name = "Node" + x + "-in-chord-ring")
        logger.info("Node id => " + x + "\t\tHashedNodeId => " + nodeHash)
        actorNodes(x) ! updateHashedNodeId(nodeHash)
        actorNodes(x) ! initializeFingerTable()
        actorNodes(x) ! joinRing(initialNode,firstNode)
      }
      logger.info("Chord nodes created.")
    }
  }
}