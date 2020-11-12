package Actors

import Actors.UserActor.createActor
import akka.actor.Actor


class UserActor(userId :Int) extends Actor {
  def receive  ={
    case createActor(userId) =>
  }
}
object UserActor{
case class createActor(userId:Int)
}
