package Actors

import Actors.ServerActor.{ getNodePos, initializeFingerTable, joinRing, sendHashedNodeId, setPredecessor, setSuccessor, succAndPred, updateHashedNodeId}
import Data.{FingerTableValue, ServerData}
import akka.actor.{Actor, ActorRef}
import Utils.Utility
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import akka.pattern._
import akka.util.Timeout

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ServerActor(hashValue:Int) extends Actor with LazyLogging{
  //The 'm' represents the m in 2^m, where it denotes the number of entries in the finger table.
  val config: Config = ConfigFactory.load()
  val numComputers: Int = config.getInt("count.zero.computers") // 2^m
  val entriesInFingerTable: Int = (Math.log(numComputers) / Math.log(2)).toInt  // m

  private var fingerTable = new mutable.HashMap[Int, FingerTableValue]
  private val nodeId: Int= -1
  private var hashedNodeId: Int = -1
  private var data: ServerData = new ServerData
  private var hashedDataKey: Int = -1
  private var existing :ActorRef = _
  private var successor: ActorRef = self
  private var predecessor: ActorRef = self

  def closestPrecedingFinger(hash: Int): ActorRef ={
    for( i <- entriesInFingerTable to 0) {
      {
        if(Utility.checkrange(hashValue, hash, fingerTable(i).successorId))
          return fingerTable(i).node
      }
    }
    self
  }

  override def receive: Receive = {
    case updateHashedNodeId(nodeId:Int) => {
      hashedNodeId = nodeId
    }
     case initializeFingerTable() => {
      for(i <- 0 until entriesInFingerTable) {
        fingerTable += (i -> FingerTableValue(((hashValue + scala.math.pow(2, i)) % numComputers).asInstanceOf[Int],self, hashValue))
        //fingerTable += (((hashedNodeId + math.pow(2, i)) % numComputers).toInt -> hashedNodeId)
      }
      println(fingerTable)
    }
    case joinRing(refnode:ActorRef) =>{
      this.existing = refnode
      implicit val timeout: Timeout = Timeout(10.seconds)
      val future = existing ? getNodePos(self,hashValue)
      val res = Await.result(future,timeout.duration).asInstanceOf[succAndPred]
      println(res)
      this.predecessor = res.pred
      this.successor = res.succ
      predecessor ! setSuccessor(self)
      successor ! setPredecessor(self)
     // implicit val timeout: Timeout = Timeout(10.seconds)
      val future1 = successor ? sendHashedNodeId
      val successorNodeid = Await.result(future1,timeout.duration)
      println(successorNodeid)
    }
    case getNodePos(refNode:ActorRef,nodeHash:Int) =>{
      logger.info("Begin =>" + fingerTable.get(0).get.start + " End => " + fingerTable.get(0).get.successorId + "ID => " + nodeHash )
      if(Utility.checkrange(fingerTable.get(0).get.start,fingerTable.get(0).get.successorId,nodeHash)){
          sender ! succAndPred(self,fingerTable.get(0).get.node )
      }else{
        implicit val timeout: Timeout = Timeout(10.seconds)
        val target = closestPrecedingFinger(nodeHash)
        val future = target ? getNodePos(refNode, nodeHash)
        val succandprec = Await.result(future,timeout.duration).asInstanceOf[succAndPred]
        sender ! (succandprec.pred, succandprec.succ)
      }
    }
    case setSuccessor(node:ActorRef)=>{
      this.successor = node
    }
    case setPredecessor(node:ActorRef) =>{
      this.predecessor = node
    }
    case sendHashedNodeId =>{
      sender ! this.hashedNodeId
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
  sealed case class updateHashedNodeId(id: Int)
  sealed case class initializeFingerTable()
  sealed case class joinRing(node :ActorRef)
  sealed case class succAndPred(succ:ActorRef, pred:ActorRef)
  sealed case class getNodePos(node : ActorRef, hash:Int)
  sealed case class setSuccessor(node:ActorRef)
  sealed case class setPredecessor(node:ActorRef)
  case object sendHashedNodeId

}

