package Actors
import Actors.ServerActor._
import akka.actor.{Actor, ActorRef}
import Utils.CommonUtils
import akka.event.Logging
import com.typesafe.config.{Config, ConfigFactory}
import akka.pattern._
import akka.util.Timeout
import com.google.gson.JsonObject

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
  val server_data = new mutable.HashMap[Int, mutable.HashMap[String,String]]

  logger.info("Initializing Finger table for Node " + hashValue)
  for(i <- 0 until entriesInFingerTable) {
    fingerTable += (i -> FingerTableValue(((hashValue + scala.math.pow(2, i)) % numComputers).asInstanceOf[Int],self, hashValue))
  }
  hashedNodeId = hashValue
  logger.info("Finger Table initialized for node " + hashValue + "Finger Table " + fingerTable )

  def closestPrecedingFinger(hash: Int): ActorRef ={
    for( i <- entriesInFingerTable-1 to 0 by -1) {
      {
        if(CommonUtils.checkrange(false,hashedNodeId, hash,false, fingerTable(i).successorId))
          return fingerTable(i).node
      }
    }
    self
  }
  def notifyOthers(): Unit = {
    logger.info("Notfying others to update their finger Table")
    for (i <- 0 until entriesInFingerTable ) {
      val position = (hashValue - BigInt(2).pow(i) + BigInt(2).pow(entriesInFingerTable) + 1) % BigInt(2).pow(entriesInFingerTable)
      successor ! UpdateFingerTables_new(position.toInt, i, self, hashValue)
    }
  }
  override def receive: Receive = {
    case updateHashedNodeId(nodeId:Int) => {
      hashedNodeId = nodeId
    }

    case joinRing(refnode:ActorRef, refNodeHash:Int) =>{
      this.existing = refnode //Arbitary Node, refNodeHash - Hash of existing node
      implicit val timeout: Timeout = Timeout(10.seconds)
      logger.info("Node {} joining the ring",hashedNodeId)
      val newfuture = existing ? find_predecessor(refNodeHash,fingerTable.get(0).get.start)
      val newRes = Await.result(newfuture,timeout.duration).asInstanceOf[succ]
      logger.info("After 1st Predecessor call for Init for node  " + hashedNodeId + " Return "+ newRes)
      this.predecessor = newRes.n
      this.successor = newRes.succ
      successor ! setPredecessor(self,hashedNodeId)
      predecessor ! setSuccessor(self,hashedNodeId)
      fingerTable.get(0).get.node = successor
      fingerTable.get(0).get.successorId = newRes.succId
      for( i <- 0 until entriesInFingerTable-1){
        if(CommonUtils.checkrange(true,hashedNodeId,fingerTable.get(i).get.successorId,false,fingerTable.get(i+1).get.start)){
          fingerTable.get(i+1).get.node = fingerTable.get(i).get.node
          fingerTable.get(i+1).get.successorId = fingerTable.get(i).get.successorId
        }else{
          val fingerFuture = existing ? find_predecessor(refNodeHash,fingerTable.get(i+1).get.start)
          val fingerRes = Await.result(fingerFuture,timeout.duration).asInstanceOf[succ]
          fingerTable.get(i+1).get.node = fingerRes.succ
          fingerTable.get(i+1).get.successorId = fingerRes.succId
        }
      }
      println("After Updation of node " + hashedNodeId + " Finger Table " + fingerTable)
      logger.info("Node joined the ring, ask others to update their finger table")
      notifyOthers()
      sender() ! "Updated Others"
    }

    case UpdateFingerTables_new(previous: Int, index: Int, nodeRef: ActorRef, nodeHash: Int) =>{
      if (nodeRef != self) {
      if(CommonUtils.checkrange(false,hashedNodeId,fingerTable.get(0).get.successorId,true,previous)){
         if(CommonUtils.checkrange(false,hashedNodeId,fingerTable.get(index).get.successorId,false,nodeHash)){
             logger.info("Index {} of node {} getting updated ", index, nodeRef)
              fingerTable.get(index).get.node = nodeRef
              fingerTable.get(index).get.successorId = nodeHash
            predecessor ! UpdateFingerTables_new(hashValue, index, nodeRef, nodeHash)
          }
        } else {
          val target = closestPrecedingFinger(previous)
          target ! UpdateFingerTables_new(previous, index, nodeRef, nodeHash)
        }
      }
    }
    case Test()=>{
      logger.info("Test  " + hashedNodeId + " " + this.fingerTable)
    }
    case ChordGlobalState(actorHashMap:mutable.HashMap[ActorRef,Int]) =>
      val table = new JsonObject
      for(i <- fingerTable){
        table.addProperty(i._2.start.toString, i._2.successorId)
      }
      val serverVariables = new JsonObject
      serverVariables.addProperty("ServerNode", hashedNodeId)
      serverVariables.addProperty("Successor", actorHashMap(successor))
      serverVariables.addProperty("Predecessor", actorHashMap(predecessor))
      serverVariables.add("FingerTable", table)
      sender ! GlobalState(serverVariables)


    case find_predecessor(refNodeHash:Int,nodeHash:Int)=>{
      logger.info("In predecessor Calling function hash " + refNodeHash + " HashNodeId " + hashedNodeId)
      if(CommonUtils.checkrange(false,refNodeHash, fingerTable.get(0).get.successorId,true,nodeHash)){
        //logger.info("succ(self) "+ succ(self))
        logger.info("Sender {} , succ( {} {} {} ", sender,self,fingerTable.get(0).get.node,fingerTable.get(0).get.successorId)
        sender ! succ(self, fingerTable.get(0).get.node,fingerTable.get(0).get.successorId)
      }else{
        implicit val timeout: Timeout = Timeout(10.seconds)
        val target = closestPrecedingFinger(nodeHash)
        val future1 = target ? find_predecessor(refNodeHash, nodeHash)
        val result1 = Await.result(future1, timeout.duration).asInstanceOf[succ]
        logger.info("Else, succ( {} {} {}) ",result1.n,result1.succ,result1.succId)
        sender ! succ(result1.n,result1.succ,result1.succId)
      }
    }
    case SearchNodeToWrite(keyHash:Int, key:String, value:String) =>{
      if (CommonUtils.checkrange(false, hashedNodeId, fingerTable.get(0).get.successorId,true, keyHash)) {
        fingerTable.get(0).get.node ! WriteDataToNode(keyHash,key, value)
      } else {
        val target = closestPrecedingFinger(keyHash)
        target ! SearchNodeToWrite(keyHash,key, value)
      }
    }
    case WriteDataToNode(keyHash:Int,key:String,value:String)=>{
      logger.info("Writing data ({} {}) with HashKey {} to node {} ", key, value,keyHash, hashedNodeId)
      if(server_data.contains(keyHash)) {
        var map = server_data(keyHash)
        server_data.put(keyHash, map += key -> value)
      } else {
        server_data.put(keyHash,  mutable.HashMap(key -> value))

      }
      println(server_data)
    }
    case getDataFromNode(keyHash:Int,key:String) =>{
      implicit val timeout: Timeout = Timeout(10.seconds)
      logger.info("Hash of the movie {}  is {} ",key, keyHash )
      logger.info("Finding node with the movie - {} hash{} in node {}",key, keyHash, hashedNodeId)
      logger.info("Movies stored under node {} are {} ", hashedNodeId, server_data)
      if(!server_data.isEmpty && server_data.contains(keyHash)) {
        logger.info("Found hash in node" + hashedNodeId)
        val map  = server_data(keyHash)
        logger.info("Found map " + map)
        if(map.contains(key))
          {
            logger.info("Found the movie {} {}in node {}", key, keyHash, hashedNodeId)
            sender ! sendValue(map(key))
          }
        else
          sender ! sendValue("Movie not found")
      }
      else {
      //logger.info("Printing server data " + server_data)
        if(CommonUtils.checkrange(false, hashedNodeId,fingerTable.get(0).get.successorId,true,keyHash)){
          val responsible_node = fingerTable.get(0).get.node ? getDataFromResNode(keyHash,key)
          val result = Await.result(responsible_node,timeout.duration).asInstanceOf[sendValue]
          sender ! sendValue(result.value)
        }
      else{
          logger.info("Finding closest preceding finger")
          val target = closestPrecedingFinger(keyHash)
          val future = target ? getDataFromNode(keyHash,key)
          val result = Await.result(future, timeout.duration).asInstanceOf[sendValue]
          sender ! sendValue(result.value)
        }
      }
    }
    case getDataFromResNode(keyHash:Int,key:String) =>{
      logger.info("Responsible Node {} for movie- {} with hash {} ", hashedNodeId, key, keyHash)
      logger.info("Responsible node's server_data {} ", server_data)
      if(!server_data.isEmpty && server_data.contains(keyHash)){
        val map = server_data(keyHash)
      if(map.contains(key)) {
        logger.info("Found the movie {} {}in node {}", key, keyHash, hashedNodeId)
        sender ! sendValue(map(key))}
      else
        sender ! sendValue("Movie not found")
    }
      else{
        sender ! sendValue("Movie not found")
      }
    }

    case setSuccessor(node:ActorRef,hashValue:Int)=>{
      logger.info("Set successor of " + "Node " + hashedNodeId + " as " + hashValue)
      this.successor = node
    }
    case setPredecessor(node:ActorRef,hashValue:Int) =>{
      logger.info("set predecessor of " + "Node " + hashedNodeId + " as " + hashValue)
      this.predecessor = node
    }
    case sendHashedNodeId =>{
      sender ! this.hashedNodeId
    }

    case _ => {
      print("Default")
    }

  }
}

object ServerActor {
  sealed case class updateHashedNodeId(id: Int)
  sealed case class joinRing(node :ActorRef, hash:Int)
  sealed case class succAndPred(succ:ActorRef, pred:ActorRef)
  sealed case class setSuccessor(node:ActorRef,hashValue:Int)
  sealed case class setPredecessor(node:ActorRef, hashValue:Int)
  case object sendHashedNodeId
  sealed case class find_successor(refNode:ActorRef,refNodeHash:Int,HashValue:Int)
  sealed case class find_predecessor(refNodeHash:Int,HashValue:Int)
  sealed case class succ(n:ActorRef,succ:ActorRef, succId:Int)
  sealed case class Test()
  sealed case class UpdateFingerTables_new(position:Int,i:Int,self:ActorRef, hashVal:Int)
  sealed case class StateFingerTable(ft:mutable.HashMap[Int, FingerTableValue])

  case class ChordGlobalState(actorHashMap:mutable.HashMap[ActorRef,Int])
  case class GlobalState(details:JsonObject)
  sealed case class SearchNodeToWrite(keyHash:Int,key:String,value:String)
  sealed case class WriteDataToNode(keyHash:Int,key:String,value:String)
  sealed case class getDataFromNode(keyHash:Int,key:String)
  sealed case class sendValue(value:String)
  sealed case class getDataFromResNode(keyHash:Int,key:String)
  case class ServerData()
  case class FingerTableValue(var start : Int, var node:ActorRef, var successorId : Int)


}

