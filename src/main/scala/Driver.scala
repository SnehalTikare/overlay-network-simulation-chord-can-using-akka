

import akka.actor._
import java.security.MessageDigest

import Actors.SupervisorActor


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

    val nodeSupervisor = actorSystem.actorOf(Props(new SupervisorActor(actorSystem, numNodes)), name = "SupervisorActor")
    nodeSupervisor ! "createChordNodes"
  }
}