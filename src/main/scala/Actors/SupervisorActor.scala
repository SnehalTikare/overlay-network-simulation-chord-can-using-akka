package Actors
import akka.actor._

class SupervisorActor(system: ActorSystem, numNodes: Int) extends Actor{
  override def receive: Receive = {
    case createChordNodes => {
      //ActorRef - Reference that points to the actor instance
      val actorNodes = new Array[ActorRef](numNodes)
      for (x <- 0 until numNodes){
       // actorNodes(x) = system.actorOf(Props(new ChordNode(x)), name = "Node" + x + "-in-chord-ring")
        //actorNodes(x) ! "createHash"
      }
      println("Chord nodes created.")
    }
  }
}