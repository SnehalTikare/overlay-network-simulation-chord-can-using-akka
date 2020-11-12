

import akka.actor._
import Actors.SupervisorActor
import com.typesafe.config.{Config, ConfigFactory}


object Driver {

  val config: Config = ConfigFactory.load()
  val numNodes: Int = config.getInt("count.numNodes")

  def main(args: Array[String]): Unit = {
    //An ActorSystem is the initial entry point into Akka.
    val actorSystem: ActorSystem = ActorSystem("Actor-System")

    val nodeSupervisor = actorSystem.actorOf(Props(new SupervisorActor(actorSystem, numNodes)), name = "SupervisorActor")
    nodeSupervisor ! "createChordNodes"
  }
}