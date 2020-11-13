package Actors

import Actors.ServerActor.{createHashedNodeId, initializeFingerTable}
import Data.{FingerTableValue, ServerData}
import akka.actor.Actor
import Utils.Utility
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable

class ServerActor extends Actor with LazyLogging{
  //The 'm' represents the m in 2^m, where it denotes the number of entries in the finger table.
  val config: Config = ConfigFactory.load()
  val numComputers: Int = config.getInt("count.zero.computers")
  val entriesInFingerTable: Int = (Math.log(numComputers) / Math.log(2)).toInt

  private var fingerTable = new mutable.LinkedHashMap[Int, Int]
  private val nodeId: Int= -1
  private var hashedNodeId: Int = -1
  private var data: ServerData = new ServerData
  private var hashedDataKey: Int = -1
  private var successor: Int = -1
  private var predecessor = -1

  override def receive: Receive = {
    case createHashedNodeId(nodeId: Int) => {
      hashedNodeId = Utility.sha1(nodeId.toString)
      logger.info("Node id => " + nodeId + "\t\tHashedNodeId => " + hashedNodeId)

    }

     case initializeFingerTable() => {
      for(i <- 0 until entriesInFingerTable) {
        fingerTable += (((hashedNodeId + math.pow(2, i)) % numComputers-1).toInt -> Integer.MIN_VALUE)
      }
      println(fingerTable)
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

object ServerActor {
  sealed case class createHashedNodeId(id: Int)
  sealed case class initializeFingerTable()
}

