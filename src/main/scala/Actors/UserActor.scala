package Actors
import java.util.concurrent.atomic.AtomicInteger

import Actors.UserActor._
import akka.actor.Actor


class UserActor(userId :Int) extends Actor {
  val readreq = new AtomicInteger()
  val writereq = new AtomicInteger()

  def receive  ={
    case Read(key:String)=>{
    readreq.addAndGet(1)
    }
    case Write(key:String, value:String)=>{
    writereq.addAndGet(1)
    }

  }
}
object UserActor{
    sealed case class Read(key:String)
    sealed case class Write(key:String, value:String)
}
