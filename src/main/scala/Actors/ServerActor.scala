package Actors

import Data.{FingerTableValue, ServerData}
import akka.actor.Actor
import Utils.Utility

import scala.collection.mutable

class ServerActor extends Actor {
  private var fingerTable = new mutable.HashMap[Int, FingerTableValue]
  private var nodeId: Int = -1
  private var hashedNodeId: String = ""
  private var data: ServerData = new ServerData
  private var hashedData: String = ""

  override def receive: Receive = {
    case createHashedNodeId => {
      hashedNodeId = Utility.md5(nodeId.toString)
    }
    case _ => {
      print("Default")
    }
  }

  def setNodeData(nodeData : ServerData, dataId : Int) : Unit = {
    data = nodeData
    hashedData = Utility.md5(dataId.toString)
  }
}


