package Actors

import Data.{FingerTableValue, ServerData}
import akka.actor.Actor
import Utils.Utility

import scala.collection.mutable

class ServerActor(id: Int, numNodes: Int) extends Actor {
  private var fingerTable = new mutable.HashMap[Int, FingerTableValue]
  private val nodeId: Int = id
  private var hashedNodeId: Int = -1
  private var data: ServerData = new ServerData
  private var hashedDataKey: Int = -1

  override def receive: Receive = {
    case createHashedNodeId => {
      hashedNodeId = Utility.md5(nodeId.toString, numNodes)
      println("Node id => " + nodeId + "\t\tHashedNodeId => " + hashedNodeId)
    }
    case _ => {
      print("Default")
    }
  }

  def setNodeData(nodeData : ServerData, dataId : Int) : Unit = {
    data = nodeData
    //hashedDataKey = Utility.md5(dataId.toString)
  }
}


