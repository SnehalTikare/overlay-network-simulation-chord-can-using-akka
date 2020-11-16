package Actors

//import Actors.ServerActor.{Test, UpdateOthers, find_predecessor, find_successor, getNodePos, initializeFingerTable, joinRing, sendHashedNodeId, setPredecessor, setSuccessor, succ, succAndPred, updateHashedNodeId}
import Actors.ServerActor._
import Data.{FingerTableValue, ServerData}
import akka.actor.{Actor, ActorRef}
import Utils.Utility
import akka.event.Logging
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import akka.pattern._
import akka.util.Timeout

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class ServerActor(hashValue:Int) extends Actor {
  //The 'm' represents the m in 2^m, where it denotes the number of entries in the finger table.
  val logger = Logging(context.system,this)
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


  for(i <- 0 until entriesInFingerTable) {
    fingerTable += (i -> FingerTableValue(((hashValue + scala.math.pow(2, i)) % numComputers).asInstanceOf[Int],self, hashValue))
    //fingerTable += (((hashedNodeId + math.pow(2, i)) % numComputers).toInt -> hashedNodeId)
  }


  def closestPrecedingFinger(hash: Int): ActorRef ={
    for( i <- entriesInFingerTable to 0) {
      {
        if(Utility.checkrange(hashValue, hash, fingerTable(i).successorId))
          return fingerTable(i).node
      }
    }
    self
  }


  def notifyOthers(): Unit = {
    for (i <- 0 until entriesInFingerTable - 1) {
      val position = (hashValue - BigInt(2).pow(i) + BigInt(2).pow(entriesInFingerTable) + 1) % BigInt(2).pow(entriesInFingerTable)
      successor ! UpdateFingerTables_new(position.toInt, i, self, hashValue)
    }
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
      implicit val timeout: Timeout = Timeout(100.seconds)
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
      logger.info("Node joined the ring, ask others to update their finger table")
      notifyOthers()
      Thread.sleep(1000)
      println("Updated after notifying others " + hashedNodeId + " "+ fingerTable)
      sender() ! "JoinedRing"
    }

    case UpdateOthers(nodeHash:Int)=>{
      logger.info("In UpdateOthers")
       for(i <- 0 until entriesInFingerTable -1 ){
         var checkindex  = (nodeHash - Math.pow(2,i).toInt)
         if(checkindex < 0)
           checkindex = numComputers - Math.abs(checkindex)
         logger.info( "i => " + i + " n - 2^i " + checkindex)
        self ! find_predecessor_update(nodeHash, checkindex ,i, self)

       }
    }
    case UpdateFingerTables_new(previous: Int, index: Int, nodeRef: ActorRef, nodeHash: Int) =>
      if (nodeRef != self) { // new node is not its own successor (usually happens only when there is only one node in chord)
        // Check if the hash position determined is in the range of the calling (The successor's) has and it's successor's hash
        //if (CommonUtils.rangeValidator(leftInclude = false, hashValue, fingerTable(0).getHash, rightInclude = true, previous)) { //I am the node just before N-2^i
          if(Utility.checkrange(hashedNodeId+1,fingerTable.get(0).get.successorId+1,previous)){
          // Check if the hash position determined is in the range of the calling (The successor's) has and it's index'th finger's hash
          //if (CommonUtils.rangeValidator(leftInclude = false, hashValue, fingerTable(index).getHash, rightInclude = false, nodeHash)) {
            if(Utility.checkrange(hashedNodeId+1,fingerTable.get(index).get.successorId,nodeHash)){
            // Update the finger table of the node
              fingerTable.get(index).get.node = nodeRef
              fingerTable.get(index).get.successorId = nodeHash
            // Notify the predecessor to update its index'th position in the finger table if required
            predecessor ! UpdateFingerTables_new(hashValue, index, nodeRef, nodeHash)
          }
        } else {
          // Find the closest preceding finger and ask it to update the finger tables for the particular node
          val target = closestPrecedingFinger(previous)
          target ! UpdateFingerTables_new(previous, index, nodeRef, nodeHash)
        }
      }
    case find_predecessor_update(refNodeHash:Int,nodeHash:Int,index:Int, node:ActorRef)=>{
      logger.info("Find Predecessor update")
      logger.info("Get "+ self)
      logger.info("FingerTable " + fingerTable)
      if(Utility.checkrange(refNodeHash, fingerTable.get(0).get.successorId,nodeHash)){
        //logger.info("succ(self) "+ succ(self))
        //sender ! succ(self, fingerTable.get(0).get.node,fingerTable.get(0).get.successorId)
        println(self + " "+ index.toString)
        logger.info("refNodeHash " + refNodeHash)
        self ! UpdateFingerTable(hashedNodeId,refNodeHash,index,node)
      }else{
        val target = closestPrecedingFinger(nodeHash)
        target ! find_predecessor_update(refNodeHash, nodeHash, index,node)
      }
    }
    case UpdateFingerTable(n:Int,s:Int, i:Int,snode:ActorRef) =>{
      logger.info("UpdateFingerTable")
      logger.info("n=> " + n + "s=> " + s + "i=> " + i + "snode=> " + snode )
          if(Utility.checkrange(n, fingerTable.get(i).get.successorId,s)){
            fingerTable.get(i).get.node = snode
            fingerTable.get(i).get.successorId = s
            predecessor ! UpdateFingerTable(n, s,i, snode)

          }
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
        implicit val timeout: Timeout = Timeout(100.seconds)
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
    case PrintState =>{
      logger.info("Snapshot of finger table of server with path {} : {}", context.self.path, fingerTable)
      println("------Printing State of the Actor-----")
      println("NodeId " + this.hashedNodeId)
      println("Successor " + this.successor.path.name)
      println("Predecessor " + this.predecessor.path.name)
      println("Finger Table " + this.fingerTable)
      val SnapData = "Successor " + predecessor.path.name.toString
      sender() ! SnapData
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
  sealed case class UpdateOthers(nodeHash:Int)
  sealed case class UpdateFingerTable(n:Int,s:Int, i:Int,snode:ActorRef)
  sealed case class find_predecessor_update(refNodeHash:Int,HashValue:Int,index:Int, node:ActorRef)
  sealed case class UpdateFingerTables_new(position:Int,i:Int,self:ActorRef, hashVal:Int)
  sealed case class PrintState()
  sealed case class StateFingerTable(ft:mutable.HashMap[Int, FingerTableValue])

}

