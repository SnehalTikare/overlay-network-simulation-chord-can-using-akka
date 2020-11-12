package Actors

import java.math.BigInteger
import java.security.MessageDigest

import Data.{FingerTableValue, ServerData}
import akka.actor.Actor

import scala.collection.mutable

class ServerActor(nodeId: Int) extends Actor {
  private var fingerTable = new mutable.HashMap[Int, FingerTableValue]
  //private var nodeId: Int = -1
  private val hashedNodeId : Int = md5(nodeId.toString)

  private var data : ServerData = new ServerData
  private var hashedData : Int = -1

  def md5(s: String): Int = {
    val m: Int = Math.ceil(Math.log(3) / Math.log(2.0)).toInt
    val x = MessageDigest.getInstance("MD5").digest(s.getBytes).map("%02x".format(_)).mkString
    val y = new BigInteger(x, 16).toString(2)

    var addressHash: Int = Integer.parseInt(y.substring(y.length() - m), 2)
    addressHash
  }

  override def receive: Receive = {
    case default => {
      println(hashedNodeId)
    }
  }
}

