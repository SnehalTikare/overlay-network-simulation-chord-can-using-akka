package Actors
import Actors.ServerActor._
import Messages.SerializableMessage
import akka.actor.{Actor, ActorRef, Props}
import Utils.{CommonUtils, SimulationUtils}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.{ExtractEntityId, ExtractShardId}
import akka.dispatch.Envelope
import akka.event.Logging
import com.typesafe.config.{Config, ConfigFactory}
import akka.pattern._
import akka.util.Helpers.Requiring
import akka.util.Timeout
import com.google.gson.JsonObject
import Utils.SimulationUtils.Command
import Utils.SimulationUtils.serverActorIdMap
import Utils.SimulationUtils.Envelope

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
  private var hashedNodeId: Int = -1
  private var existing :ActorRef = _
  private var successor: ActorRef = self
  private var predecessor: ActorRef = self
  val server_data = new mutable.HashMap[Int, mutable.HashMap[String,String]]

  //Initialize the finger table with successor for each finger as itself
  logger.info("Initializing Finger table for Node " + hashValue)
  for(i <- 0 until entriesInFingerTable) {
    fingerTable += (i -> FingerTableValue(((hashValue + scala.math.pow(2, i)) % numComputers).asInstanceOf[Int],self, hashValue))
  }
  hashedNodeId = hashValue //Hash value of the node
  logger.info("Finger Table initialized for node " + hashValue + "Finger Table " + fingerTable )

  //Find the closest preceding finger for the given id
  def closestPrecedingFinger(hash: Int): ActorRef ={
    for( i <- entriesInFingerTable-1 to 0 by -1) {
      {
        if(CommonUtils.checkrange(false,hashedNodeId, hash,false, fingerTable(i).successorId))
          return fingerTable(i).node
      }
    }
    self
  }
  //Update the finger tables of other nodes when the node joins the ring
  def notifyOthers(): Unit = {
    logger.info("Notfying others to update their finger Table")
    for (i <- 0 until entriesInFingerTable ) {
      val position = (hashValue - BigInt(2).pow(i) + BigInt(2).pow(entriesInFingerTable) + 1) % BigInt(2).pow(entriesInFingerTable)
      successor ! SimulationUtils.Envelope(serverActorIdMap.get(successor),UpdateFingerTables_new(position.toInt, i, self, hashValue))
    }
  }
  override def receive: Receive = {
    case updateHashedNodeId(nodeId:Int) => {
      hashedNodeId = nodeId
    }

      //refNode -  Arbitary node used to initialize the local table of the newly joined node
    case joinRing(refnode:ActorRef, refNodeHash:Int) =>{
      this.existing = refnode //Arbitary Node, refNodeHash - Hash of existing node
      implicit val timeout: Timeout = Timeout(10.seconds)
      logger.info("Node {} joining the ring",hashedNodeId)
      //Find the successor of the newly joined node using the arbitary node
      val newfuture = existing ? SimulationUtils.Envelope(serverActorIdMap.get(existing),find_predecessor(refNodeHash,fingerTable.get(0).get.start))
      val newRes = Await.result(newfuture,timeout.duration).asInstanceOf[succ]
      logger.info("After 1st Predecessor call for Init for node  " + hashedNodeId + " Return "+ newRes)
      this.predecessor = newRes.n
      this.successor = newRes.succ
      //Set predecessor of the successor as the current node
      successor ! SimulationUtils.Envelope(serverActorIdMap.get(successor),setPredecessor(self,hashedNodeId))
      //Set successor of the predecessor as the current node
      predecessor ! SimulationUtils.Envelope(serverActorIdMap.get(predecessor),setSuccessor(self,hashedNodeId))
      fingerTable.get(0).get.node = successor //First finger in the finger table refers to the successor
      fingerTable.get(0).get.successorId = newRes.succId
      //Update other fingers
      for( i <- 0 until entriesInFingerTable-1){
        if(CommonUtils.checkrange(true,hashedNodeId,fingerTable.get(i).get.successorId,false,fingerTable.get(i+1).get.start)){
          fingerTable.get(i+1).get.node = fingerTable.get(i).get.node
          fingerTable.get(i+1).get.successorId = fingerTable.get(i).get.successorId
          //logger.info("Passed if in join ring\n\n")
        }else{
          //logger.info("In else in join ring \n\n")
          val fingerFuture = existing ? SimulationUtils.Envelope(serverActorIdMap.get(existing),find_predecessor(refNodeHash,fingerTable.get(i+1).get.start))
          val fingerRes = Await.result(fingerFuture,timeout.duration).asInstanceOf[succ]
          //logger.info("In else in join ring part 2 \n\n")
          fingerTable.get(i+1).get.node = fingerRes.succ
          fingerTable.get(i+1).get.successorId = fingerRes.succId

        }
      }
      println("After Updation of node " + hashedNodeId + " Finger Table " + fingerTable)
      logger.info("Node joined the ring, ask others to update their finger table")
      //Signal others to update their finger table
      notifyOthers()
      sender() ! SimulationUtils.Envelope(serverActorIdMap.get(sender), StringMsg("Updated Others"))
    }

      //Update the finger table of others nodes in the ring
    case UpdateFingerTables_new(previous: Int, index: Int, nodeRef: ActorRef, nodeHash: Int) =>{
      if (nodeRef != self) {
      if(CommonUtils.checkrange(false,hashedNodeId,fingerTable.get(0).get.successorId,true,previous)){
         if(CommonUtils.checkrange(false,hashedNodeId,fingerTable.get(index).get.successorId,false,nodeHash)){
             logger.info("Index {} of node {} getting updated ", index, nodeRef)
              fingerTable.get(index).get.node = nodeRef
              fingerTable.get(index).get.successorId = nodeHash
            predecessor ! SimulationUtils.Envelope(serverActorIdMap.get(predecessor),UpdateFingerTables_new(hashValue, index, nodeRef, nodeHash))
          }
        } else {
          val target = closestPrecedingFinger(previous)
          target ! SimulationUtils.Envelope(serverActorIdMap.get(target),UpdateFingerTables_new(previous, index, nodeRef, nodeHash))
        }
      }
    }

      //Store the current state of the actor to a json object
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
      sender ! SimulationUtils.Envelope(serverActorIdMap.get(sender),GlobalState(serverVariables))

    //Find the predecessor of node 'nodehash' using the 'refNodeHash'
    case find_predecessor(refNodeHash:Int,nodeHash:Int)=>{
      logger.info("In predecessor Calling function hash " + refNodeHash + " HashNodeId " + hashedNodeId)
      if(CommonUtils.checkrange(false,refNodeHash, fingerTable.get(0).get.successorId,true,nodeHash)){
        logger.info("Sender {} , succ( {} {} {} ", sender,self,fingerTable.get(0).get.node,fingerTable.get(0).get.successorId)
        sender ! SimulationUtils.Envelope(serverActorIdMap.get(sender),succ(self, fingerTable.get(0).get.node,fingerTable.get(0).get.successorId))
        //logger.info("In find_pred end of if \n")
      }else{
        //logger.info("In find_pred start of else \n\n")
        implicit val timeout: Timeout = Timeout(50.seconds)
        val target = closestPrecedingFinger(nodeHash)
        val future1 = target ? SimulationUtils.Envelope(serverActorIdMap.get(target),find_predecessor(refNodeHash, nodeHash))
        val result1 = Await.result(future1, timeout.duration).asInstanceOf[succ]
        logger.info("Else, succ( {} {} {}) ",result1.n,result1.succ,result1.succId)
        sender ! SimulationUtils.Envelope(serverActorIdMap.get(sender),succ(result1.n,result1.succ,result1.succId))
        //logger.info("In find_pred end of else \n\n")
      }
    }
      //Find the node that will store the data with hashed value - keyHash
    case SearchNodeToWrite(keyHash:Int, key:String, value:String) =>{
      if (CommonUtils.checkrange(false, hashedNodeId, fingerTable.get(0).get.successorId,true, keyHash)) {
        fingerTable.get(0).get.node ! SimulationUtils.Envelope(serverActorIdMap.get(fingerTable.get(0).get.node),WriteDataToNode(keyHash,key, value))
      } else {
        val target = closestPrecedingFinger(keyHash)
        target ! SimulationUtils.Envelope(serverActorIdMap.get(target),SearchNodeToWrite(keyHash,key, value))
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
            sender ! SimulationUtils.Envelope(serverActorIdMap.get(sender),sendValue(map(key)))
          }
        else
          sender ! SimulationUtils.Envelope(serverActorIdMap.get(sender),sendValue("Movie not found"))
      }
      else {
      //logger.info("Printing server data " + server_data)
        if(CommonUtils.checkrange(false, hashedNodeId,fingerTable.get(0).get.successorId,true,keyHash)){
          val responsible_node = fingerTable.get(0).get.node ? getDataFromResNode(keyHash,key)
          val result = Await.result(responsible_node,timeout.duration).asInstanceOf[sendValue]
          sender ! SimulationUtils.Envelope(serverActorIdMap.get(sender),sendValue(result.value))
        }
      else{
          logger.info("Finding closest preceding finger")
          val target = closestPrecedingFinger(keyHash)
          val future = target ? SimulationUtils.Envelope(serverActorIdMap.get(target),getDataFromNode(keyHash,key))
          val result = Await.result(future, timeout.duration).asInstanceOf[sendValue]
          sender ! SimulationUtils.Envelope(serverActorIdMap.get(sender),sendValue(result.value))
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
        sender ! SimulationUtils.Envelope(serverActorIdMap.get(sender),sendValue(map(key)))
      }
      else
        sender ! SimulationUtils.Envelope(serverActorIdMap.get(sender),sendValue("Movie not found"))
    }
      else{
        sender ! SimulationUtils.Envelope(serverActorIdMap.get(sender),sendValue("Movie not found"))
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


    case _ => {
      print("Default")
    }

  }
}

object ServerActor {

import SimulationUtils.Envelope

  def props(hashValue : Int):Props = Props(new ServerActor(hashValue))

  val entityIdExtractor:ExtractEntityId ={
    case Envelope(serverActorId,command) => (serverActorId.value.toString,command)
  }

  val shardIdExtractor:ExtractShardId ={
    case Envelope(userActorId, _) => Math.abs(userActorId.value.toString.hashCode % 1).toString
    case ShardRegion.StartEntity(entityId) =>Math.abs(entityId.hashCode % 1).toString
  }

  sealed case class joinRing(node :ActorRef, hash:Int) extends Command
  sealed case class UpdateFingerTables_new(position:Int,i:Int,self:ActorRef, hashVal:Int) extends Command
  case class ChordGlobalState(actorHashMap:mutable.HashMap[ActorRef,Int]) extends Command
  sealed case class find_successor(refNode:ActorRef,refNodeHash:Int,HashValue:Int) extends Command
  sealed case class find_predecessor(refNodeHash:Int,HashValue:Int) extends Command


  sealed case class updateHashedNodeId(id: Int) extends Command
  sealed case class succAndPred(succ:ActorRef, pred:ActorRef) extends Command
  sealed case class setSuccessor(node:ActorRef,hashValue:Int) extends Command
  sealed case class setPredecessor(node:ActorRef, hashValue:Int) extends Command

  sealed case class succ(n:ActorRef,succ:ActorRef, succId:Int) extends Command
  sealed case class StateFingerTable(ft:mutable.HashMap[Int, FingerTableValue]) extends Command

  case class GlobalState(details:JsonObject) extends Command

  sealed case class SearchNodeToWrite(keyHash:Int,key:String,value:String) extends Command
  sealed case class WriteDataToNode(keyHash:Int,key:String,value:String) extends Command
  sealed case class getDataFromNode(keyHash:Int,key:String) extends Command
  sealed case class sendValue(value:String) extends Command
  sealed case class getDataFromResNode(keyHash:Int,key:String) extends Utils.SimulationUtils.Command
  case class ServerData() extends Command
  case class FingerTableValue(var start : Int, var node:ActorRef, var successorId : Int) extends Command
  case class StringMsg(msg: String) extends Command
}

