//object Driver {
//    def main(args: Array[String]) {
//      print("Hello World")
//      print("test")
//    }
//}

import akka.actor._
import java.security.MessageDigest

class NodeSupervisor(system: ActorSystem, numNodes: Int) extends Actor{
  override def receive: Receive = {
    case createChordNodes => {
      //ActorRef - Reference that points to the actor instance
      val actorNodes = new Array[ActorRef](numNodes)
      for (x <- 0 until numNodes){
        actorNodes(x) = system.actorOf(Props(new ChordNode(x)), name = "Node" + x + "-in-chord-ring")
        actorNodes(x) ! "createHash"
      }
      println("Chord nodes created.")
    }
  }
}
class ChordNode(id: Int) extends Actor {
  val nodeId: Int = id
  var nodeHah : String = ""
  val nodeData : String = "Data in node "+id
  //Generates an m-bit identifier for node using md5 hash function.
  def md5(s: String): String = {
    MessageDigest.getInstance("MD5").digest(s.getBytes).toString
  }

  override def receive: Receive = {
    case createHash => {
      nodeHah = md5(nodeId.toString)
      println("Node Id: " + nodeId + " HashVal = " + nodeHah)
    }
    case _ => {
      print("Default")
    }
  }
}


object Driver {
  def main(args: Array[String]): Unit = {
    val numNodes = 16 //Hello from Rahul
    //val numUsers = 3
    //val numRequests = 2

    //An ActorSystem is the initial entry point into Akka.
    val actorSystem: ActorSystem = ActorSystem("Actor-System")

    val nodeSupervisor = actorSystem.actorOf(Props(new NodeSupervisor(actorSystem, numNodes)), name = "NodeActors")
    nodeSupervisor ! "createChordNodes"
  }
}