

import akka.actor._
import Actors.SupervisorActor
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging


/*
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
*/


object Driver extends LazyLogging {
  val config: Config = ConfigFactory.load()
  val numNodes: Int = config.getInt("count.numNodes")

  def main(args: Array[String]): Unit = {
    //An ActorSystem is the initial entry point into Akka.
    val actorSystem: ActorSystem = ActorSystem("Actor-System")

    val nodeSupervisor = actorSystem.actorOf(Props(new SupervisorActor(actorSystem, numNodes)), name = "SupervisorActor")
    nodeSupervisor ! "createChordNodes"
  }
}