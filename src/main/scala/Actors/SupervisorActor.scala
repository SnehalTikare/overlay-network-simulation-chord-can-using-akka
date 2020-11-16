package Actors
import Actors.ServerActor._
import Utils.Utility
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

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
      Thread.sleep(10)
      //actorNodes(0) ! initializeFingerTable()
      for (x <- 1 until numNodes){
        //var nodeId = "n" + x
       // var nodeId = random.nextInt(Integer.MAX_VALUE)
        val nodeHash = Utility.sha1("n"+x)
        actorNodes(x) = system.actorOf(Props(new ServerActor(nodeHash)), name = "Node" + x + "-in-chord-ring")
        logger.info("Node id => " + x + "\t\tHashedNodeId => " + nodeHash)
        actorNodes(x) ! updateHashedNodeId(nodeHash)
        //actorNodes(x) ! initializeFingerTable()
        //actorNodes(x) ! joinRing(initialNode,firstNode)
        //Thread.sleep(10)
        //logger.info("Node joined the Ring " + actorNodes(x) + "Hash Value " + nodeHash)
        implicit val timeout: Timeout = Timeout(100.seconds)
        val future_ring = actorNodes(x) ? joinRing(initialNode,firstNode)
        val resulstring = Await.result(future_ring, timeout.duration)
        logger.info("Nodes successfully updated after node "+  nodeHash + " join "+ resulstring)
        actorNodes(0) ! Test()

        //actorNodes(x) ! UpdateOthers(nodeHash)
       // Thread.sleep(10000)
      }


//      logger.info("Chord nodes created.")
//      implicit val timeout: Timeout = Timeout(100.seconds)
//      val future = actorNodes(0) ? PrintState
//      val result1 = Await.result(future, timeout.duration)
//      println(" Snapshot " + result1 + " End ")
      //actorNodes(0) ! PrintState
      //Thread.sleep(100000)
//      for( x <- 0 until numNodes){
//        actorNodes(x) ! Print
//      }
    }
  }
}