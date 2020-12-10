package com.CHORD.Actors
import com.CHORD.Messages.SerializableMessage
import com.CHORD.Actors.ServerActor._
import akka.actor.{Actor, ActorRef}
import Utils.CommonUtils
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.{ExtractEntityId, ExtractShardId}
import akka.event.Logging
import com.typesafe.config.{Config, ConfigFactory}
import akka.pattern._
import akka.util.Timeout
import com.google.gson.JsonObject

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object ServerActor {
  sealed trait Command  extends SerializableMessage
  case class Envelope(ActorId: Int, command: Command)  extends SerializableMessage

  case class TestSharding(name:String) extends Command

  sealed case class updateHashedNodeId(id: Int) extends Command
  sealed case class joinRing(ShardRegion :ActorRef, hash:Int) extends Command
  sealed case class succAndPred(succ:ActorRef, pred:ActorRef) extends Command
  sealed case class setSuccessor(node:ActorRef,hashValue:Int) extends Command
  sealed case class setPredecessor(node:ActorRef, hashValue:Int) extends Command
  case object sendHashedNodeId extends Command
  sealed case class find_successor(refNode:ActorRef,refNodeHash:Int,HashValue:Int) extends Command
  sealed case class find_predecessor(shardRegion:ActorRef,refNodeHash:Int,HashValue:Int) extends Command
  sealed case class succ(predID:Int,n:ActorRef,succ:ActorRef, succId:Int) extends Command
  sealed case class Test() extends Command
  sealed case class UpdateFingerTables_new(shardRegion:ActorRef,position:Int,i:Int,self:ActorRef, hashVal:Int) extends Command
  sealed case class StateFingerTable(ft:mutable.HashMap[Int, FingerTableValue]) extends Command

  case class ChordGlobalState(actorHashMap:mutable.HashMap[ActorRef,Int]) extends Command
  case class GlobalState(details:JsonObject) extends Command

  sealed case class SearchNodeToWrite(shardRegion:ActorRef,keyHash:Int,key:String,value:String) extends Command
  sealed case class WriteDataToNode(keyHash:Int,key:String,value:String) extends Command
  sealed case class getDataFromNode(shardRegion:ActorRef,keyHash:Int,key:String) extends Command
  sealed case class sendValue(value:String) extends Command
  sealed case class getDataFromResNode(keyHash:Int,key:String) extends Command
  case class ServerData() extends Command
  case class FingerTableValue(var start : Int, var node:ActorRef, var successorId : Int) extends Command

  val numberOfShards = 100
  val entityIdExtractor:ExtractEntityId ={
    case Envelope(hashedNodeId,command) => (hashedNodeId.toString,command)
  }

  val shardIdExtractor:ExtractShardId ={
    case Envelope(hashedNodeId, _) => Math.abs(hashedNodeId % numberOfShards).toString
    case ShardRegion.StartEntity(hashedNodeId) =>Math.abs(hashedNodeId.toLong % numberOfShards).toString
  }

}
class ServerActor() extends Actor {
  //The 'm' represents the m in 2^m, where it denotes the number of entries in the finger table.
  val logger = Logging(context.system,this)
  val config: Config = ConfigFactory.load()
  val numComputers: Int = config.getInt("count.zero.computers") // 2^m
  val entriesInFingerTable: Int = (Math.log(numComputers) / Math.log(2)).toInt  // m

  private var fingerTable = new mutable.HashMap[Int, FingerTableValue]
  private val nodeId: Int= -1
  var hashedNodeId: Int = (self.path.name).toInt
  private var data: ServerData = new ServerData
  private var hashedDataKey: Int = -1
  private var existing :ActorRef = _
  private var successor: ActorRef = self
  private var predecessor: ActorRef = self
  private var succerssorID:Int = hashedNodeId
  private var predecessorID:Int = hashedNodeId
  val server_data = new mutable.HashMap[Int, mutable.HashMap[String,String]]
  //hashedNodeId = hashValue
  //Initialize the finger table with successor for each finger as itself
  logger.info("Initializing Finger table for Node " + hashedNodeId)
  for(i <- 0 until entriesInFingerTable) {
    fingerTable += (i -> FingerTableValue(((hashedNodeId + scala.math.pow(2, i)) % numComputers).asInstanceOf[Int],self, hashedNodeId))
  }
  //Hash value of the node
  logger.info("Finger Table initialized for node " + hashedNodeId + "Finger Table " + fingerTable )

  //Find the closest preceding finger for the given id
  def closestPrecedingFinger(hash: Int): Int ={
    for( i <- entriesInFingerTable-1 to 0 by -1) {
      {
        if(CommonUtils.checkrange(false,hashedNodeId, hash,false, fingerTable(i).successorId))
          return fingerTable(i).successorId
      }
    }
    hashedNodeId
  }
  //Update the finger tables of other nodes when the node joins the ring
  def notifyOthers(shardRegion:ActorRef): Unit = {
    logger.info("Notfying others to update their finger Table")
    for (i <- 0 until entriesInFingerTable ) {
      val position = (hashedNodeId - BigInt(2).pow(i) + BigInt(2).pow(entriesInFingerTable) + 1) % BigInt(2).pow(entriesInFingerTable)
      shardRegion ! Envelope(succerssorID,UpdateFingerTables_new(shardRegion,position.toInt, i, self, hashedNodeId))
    }
  }
  override def receive: Receive = {
    case TestSharding(name:String) =>logger.info(s"TestingSharding ${name}")
    case updateHashedNodeId(nodeId:Int) => {
      hashedNodeId = nodeId
    }

      //refNode -  Arbitary node used to initialize the local table of the newly joined node
    case joinRing(shardRegion:ActorRef, refNodeHash:Int) =>{
      if(hashedNodeId!=refNodeHash){
      //this.existing = refnode //Arbitary Node, refNodeHash - Hash of existing node
      implicit val timeout: Timeout = Timeout(10.seconds)
      logger.info("Node {} joining the ring",hashedNodeId)
      //Find the successor of the newly joined node using the arbitary node
      val newfuture = shardRegion ? Envelope(refNodeHash,find_predecessor(shardRegion,refNodeHash,fingerTable.get(0).get.start))
      val newRes = Await.result(newfuture,timeout.duration).asInstanceOf[succ]
      logger.info("After 1st Predecessor call for Init for node  " + hashedNodeId + " Return "+ newRes)
        this.predecessorID = newRes.predID
        logger.info(s"Set predecessor of  ${hashedNodeId} as ${predecessorID}")
        this.succerssorID = newRes.succId
        logger.info(s"Set successor of ${hashedNodeId} as ${succerssorID}")
      this.predecessor = newRes.n
      this.successor = newRes.succ
      //Set predecessor of the successor as the current node
        shardRegion ! Envelope(succerssorID,setPredecessor(self,hashedNodeId))
      //Set successor of the predecessor as the current node
        shardRegion ! Envelope(predecessorID,setSuccessor(self,hashedNodeId))
      fingerTable.get(0).get.node = successor //First finger in the finger table refers to the successor
      fingerTable.get(0).get.successorId = newRes.succId
      //Update other fingers
      for( i <- 0 until entriesInFingerTable-1){
        if(CommonUtils.checkrange(true,hashedNodeId,fingerTable.get(i).get.successorId,false,fingerTable.get(i+1).get.start)){
          fingerTable.get(i+1).get.node = fingerTable.get(i).get.node
          fingerTable.get(i+1).get.successorId = fingerTable.get(i).get.successorId
        }else{
          val fingerFuture = shardRegion ? Envelope(refNodeHash,find_predecessor(shardRegion,refNodeHash,fingerTable.get(i+1).get.start))
          val fingerRes = Await.result(fingerFuture,timeout.duration).asInstanceOf[succ]
          fingerTable.get(i+1).get.node = fingerRes.succ
          fingerTable.get(i+1).get.successorId = fingerRes.succId
        }
      }
//      println("After Updation of node " + hashedNodeId + " Finger Table " + fingerTable)
//      logger.info("Node joined the ring, ask others to update their finger table")
//      //Signal others to update their finger table
      notifyOthers(shardRegion)
      }
      sender() ! "Updated Others"
    }

      //Update the finger table of others nodes in the ring
    case UpdateFingerTables_new(shardRegion:ActorRef,previous: Int, index: Int, nodeRef: ActorRef, nodeHash: Int) =>{
      if (nodeRef != self) {
      if(CommonUtils.checkrange(false,hashedNodeId,fingerTable.get(0).get.successorId,true,previous)){
         if(CommonUtils.checkrange(false,hashedNodeId,fingerTable.get(index).get.successorId,false,nodeHash)){
             logger.info("Index {} of node {} getting updated ", index, nodeRef)
              fingerTable.get(index).get.node = nodeRef
              fingerTable.get(index).get.successorId = nodeHash
            shardRegion ! Envelope(predecessorID,UpdateFingerTables_new(shardRegion,hashedNodeId, index, nodeRef, nodeHash))
          }
        } else {
          val target = closestPrecedingFinger(previous)
          shardRegion  ! Envelope(target,UpdateFingerTables_new(shardRegion,previous, index, nodeRef, nodeHash))
        }
      }
    }
    case Test()=>{
      logger.info("Test  " + hashedNodeId + " " + this.fingerTable)
    }
      //Store the current state of the actor to a json object
    case ChordGlobalState(actorHashMap:mutable.HashMap[ActorRef,Int]) =>
      val table = new JsonObject
      for(i <- fingerTable){
        table.addProperty(i._2.start.toString, i._2.successorId)
      }
      val serverVariables = new JsonObject
      serverVariables.addProperty("ServerNode", hashedNodeId)
      serverVariables.addProperty("Successor", succerssorID)
      serverVariables.addProperty("Predecessor", predecessorID)
      serverVariables.add("FingerTable", table)
      sender ! GlobalState(serverVariables)

    //Find the predecessor of node 'nodehash' using the 'refNodeHash'
    case find_predecessor(shardRegion:ActorRef,refNodeHash:Int,nodeHash:Int)=>{
      logger.info("In predecessor Calling function hash " + refNodeHash + " HashNodeId " + hashedNodeId)
      if(CommonUtils.checkrange(false,refNodeHash, fingerTable.get(0).get.successorId,true,nodeHash)){
        logger.info("Sender {} , succ( {} {} {} ", sender,self,fingerTable.get(0).get.node,fingerTable.get(0).get.successorId)
        sender ! succ(hashedNodeId,self, fingerTable.get(0).get.node,fingerTable.get(0).get.successorId)
      }else{
        implicit val timeout: Timeout = Timeout(10.seconds)
        val target = closestPrecedingFinger(nodeHash)
        val future1 = (shardRegion ? Envelope(target,find_predecessor(shardRegion,refNodeHash, nodeHash)))
        val result1 = Await.result(future1, timeout.duration).asInstanceOf[succ]
        logger.info("Else, succ( {} {} {}) ",result1.n,result1.succ,result1.succId)
        sender ! succ(result1.predID,result1.n,result1.succ,result1.succId)
      }
    }
      //Find the node that will store the data with hashed value - keyHash
    case SearchNodeToWrite(shardRegion:ActorRef,keyHash:Int, key:String, value:String) =>{
      if (CommonUtils.checkrange(false, hashedNodeId, fingerTable.get(0).get.successorId,true, keyHash)) {
        shardRegion ! Envelope(fingerTable.get(0).get.successorId,WriteDataToNode(keyHash,key, value))
      } else {
        val target = closestPrecedingFinger(keyHash)
        shardRegion ! Envelope(target,SearchNodeToWrite(shardRegion,keyHash,key, value))
      }
    }
      //Write the data to the node responsible for the key , successor(k)
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

    case getDataFromNode(shardRegion:ActorRef,keyHash:Int,key:String) =>{
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
          val responsible_node = shardRegion ? Envelope(fingerTable.get(0).get.successorId, getDataFromResNode(keyHash,key))
          val result = Await.result(responsible_node,timeout.duration).asInstanceOf[sendValue]
          sender ! sendValue(result.value)
        }
      else{
          logger.info("Finding closest preceding finger")
          val target = closestPrecedingFinger(keyHash)
          val future = shardRegion ? Envelope(target,getDataFromNode(shardRegion,keyHash,key))
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
      succerssorID = hashValue
    }
    case setPredecessor(node:ActorRef,hashValue:Int) =>{
      logger.info("set predecessor of " + "Node " + hashedNodeId + " as " + hashValue)
      this.predecessor = node
      predecessorID = hashValue
    }
    case sendHashedNodeId =>{
      sender ! this.hashedNodeId
    }

    case _ => {
      print("Default")
    }

  }
}


