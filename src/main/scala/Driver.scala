

import akka.actor._
import java.security.MessageDigest

import Actors.SupervisorActor


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