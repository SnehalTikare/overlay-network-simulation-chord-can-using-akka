package Actors

import java.security.MessageDigest

import Data.{FingerTableValue, ServerData}
import akka.actor.Actor

import scala.collection.mutable

class ServerActor(nodeId: Int) extends Actor {
  private var fingerTable = new mutable.HashMap[Int, FingerTableValue]
  //private var nodeId: Int = -1
  private var hashedNodeId : Int = -1
  private var data : ServerData = new ServerData
  private var hashedData : Int = -1

  def md5(s: String): String = {
    MessageDigest.getInstance("MD5").digest(s.getBytes).toString
  }

  override def receive: Receive = ???
}

