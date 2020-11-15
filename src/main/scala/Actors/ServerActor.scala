package Actors

import Actors.ServerActor.{Test, find_predecessor, find_successor, getNodePos, initializeFingerTable, joinRing, sendHashedNodeId, setPredecessor, setSuccessor, succ, succAndPred, updateHashedNodeId}
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
    case joinRing(refnode:ActorRef, refNodeHash:Int) =>{
      this.existing = refnode
      implicit val timeout: Timeout = Timeout(10.seconds)
      println("Existing =>" + existing)
      println("Self =>" + self + " HashValue =>" +hashValue)
     // val newfuture = existing ? find_successor(refnode,refNodeHash,fingerTable.get(0).get.start)
     val newfuture = existing ? find_predecessor(refNodeHash,fingerTable.get(0).get.start)
      val newRes = Await.result(newfuture,timeout.duration).asInstanceOf[succ]
      println("NewRes " + newRes)
      this.predecessor = newRes.n
      this.successor = newRes.succ
      //predecessor ! setSuccessor(self)
      successor ! setPredecessor(self)
      val future1 = successor ? sendHashedNodeId
      val successorNodeid = Await.result(future1,timeout.duration).asInstanceOf[Int]
      fingerTable.get(0).get.node = successor
      fingerTable.get(0).get.successorId = successorNodeid

      for( i <- 0 until entriesInFingerTable-1){
        logger.info("i " + i)
        logger.info("Finger Table "+  i + " "+ fingerTable.get(i))
        if(Utility.checkrange(refNodeHash,fingerTable.get(i).get.successorId,fingerTable.get(i+1).get.start)){
          logger.info("Inside for if")
          fingerTable.get(i+1).get.node = fingerTable.get(i).get.node
          fingerTable.get(i+1).get.successorId = fingerTable.get(i).get.successorId
        }else{
          logger.info("Inside for else")
          val fingerFuture = existing ? find_predecessor(refNodeHash,fingerTable.get(i+1).get.start)
          val fingerRes = Await.result(fingerFuture,timeout.duration).asInstanceOf[succ]
          fingerTable.get(i+1).get.node = fingerRes.succ
          fingerTable.get(i+1).get.successorId = fingerRes.succId
        }

      }
      println("After Updation " +fingerTable)
      println("done")
     /* val future = existing ? getNodePos(self,hashValue)
      val res = Await.result(future,timeout.duration).asInstanceOf[succAndPred]
      println(res)
      this.predecessor = res.pred
      this.successor = res.succ
      predecessor ! setSuccessor(self)
      successor ! setPredecessor(self)

     // implicit val timeout: Timeout = Timeout(10.seconds)
      val future1 = successor ? sendHashedNodeId
      val successorNodeid = Await.result(future1,timeout.duration).asInstanceOf[Int]
      println(successorNodeid)
      fingerTable.get(0).get.node = successor
      fingerTable.get(0).get.successorId = successorNodeid
      println("After Updation =>" + fingerTable)
      for( i <- 1 to entriesInFingerTable - 1){
        if(Utility.checkrange(hashedNodeId,fingerTable.get(i).get.successorId,fingerTable.get(i+1).get.start)){
            fingerTable.get(i+1).get.node = fingerTable.get(i).get.node
          fingerTable.get(i+1).get.successorId = fingerTable.get(i).get.successorId
        }else{
          val future2 = existing ? getNodePos(fingerTable.get(i+1).get.node,fingerTable.get(i+1).get.successorId)
          val res1 = Await.result(future2,timeout.duration).asInstanceOf[succAndPred]
          fingerTable.get(i+1).get.node = res1.succ
          val succNode = res1.succ ? sendHashedNodeId
          val successorNodeid1 = Await.result(succNode,timeout.duration).asInstanceOf[Int]
          fingerTable.get(i+1).get.successorId = successorNodeid1
        }
      }*/
    }
    case updateOther
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
    case find_successor(refNode:ActorRef,refNodeHash:Int,nodeHash:Int)=>{
//      logger.info("Find Successor")
//     // refNode ! Test()
//      implicit val timeout: Timeout = Timeout(15.seconds)
//      //logger.info("Calling  Predecessor" + self + "RefNodeHash=>"+refNodeHash + "NdeHash=>" + nodeHash)
//      //logger.info("Succ Finger" + fingerTable)
//      //println("Successor " + fingerTable.get(0).get.successorId)
//      val future = self ? find_predecessor(fingerTable,refNodeHash,nodeHash)
//      logger.info("Returned from Predecessor" + future)
//      val result  = Await.result(future, timeout.duration).asInstanceOf[succ]
//      logger.info("Result" + result)
//      sender ! result.succ
    }
    case Test()=>{
      logger.info("Test  " + this.fingerTable)
    }

    case find_predecessor(refNodeHash:Int,nodeHash:Int)=>{
      logger.info("Find Predecessor")
      logger.info("Get "+ self)
      if(Utility.checkrange(refNodeHash, fingerTable.get(0).get.successorId,nodeHash)){
        logger.info("If statement")
        //logger.info("succ(self) "+ succ(self))
        sender ! succ(self, fingerTable.get(0).get.node,fingerTable.get(0).get.successorId)
      }else{
        logger.info("else statement")
        implicit val timeout: Timeout = Timeout(10.seconds)
        val target = closestPrecedingFinger(nodeHash)
        val future1 = target ? find_predecessor(refNodeHash, nodeHash)
        val result1 = Await.result(future1, timeout.duration).asInstanceOf[succ]
        sender ! (result1.n,result1.succ,result1.succId)
      }

    }
    case setSuccessor(node:ActorRef)=>{
      logger.info("set successor " + "Node " + self + "Successor " + node)
      this.successor = node
    }
    case setPredecessor(node:ActorRef) =>{
      logger.info("set predecessor " + "Node " + self + "predecessor " + node)
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
  sealed case class joinRing(node :ActorRef, hash:Int)
  sealed case class succAndPred(succ:ActorRef, pred:ActorRef)
  sealed case class getNodePos(node : ActorRef, hash:Int)
  sealed case class setSuccessor(node:ActorRef)
  sealed case class setPredecessor(node:ActorRef)
  case object sendHashedNodeId
  sealed case class find_successor(refNode:ActorRef,refNodeHash:Int,HashValue:Int)
  sealed case class find_predecessor(refNodeHash:Int,HashValue:Int)
  sealed case class succ(n:ActorRef,succ:ActorRef, succId:Int)
  sealed case class Test()
  sealed case class UpdateOther()
  sealed case class UpdateFingerTable()

}

