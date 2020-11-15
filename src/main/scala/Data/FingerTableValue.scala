package Data

import akka.actor.ActorRef

case class FingerTableValue(var start : Int, var node:ActorRef, var successorId : Int)
