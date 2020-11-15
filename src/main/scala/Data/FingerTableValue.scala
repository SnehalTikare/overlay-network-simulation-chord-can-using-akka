package Data

import akka.actor.ActorRef

case class FingerTableValue(start : Int, node:ActorRef, successorId : Int)
