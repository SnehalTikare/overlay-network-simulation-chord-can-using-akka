package com.CAN.Actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.sharding.ShardRegion.{ExtractEntityId, ExtractShardId}
import akka.cluster.sharding.{ClusterSharding, ShardRegion}
import akka.pattern.{ask, pipe}
import akka.util.Helpers.Requiring
import akka.util.Timeout
import com.CAN.Driver.clusterActorSystem
import com.CAN.helper.Bootstrap.entityIDMap
import com.CAN.helper.{Bootstrap, Coordinate}
import com.CHORD.Messages.SerializableMessage
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object NodeActor {
  sealed trait Command  extends SerializableMessage
  case class Envelope(nodeActorId: Int, command: Command)  extends SerializableMessage
  case object printState extends Command
  case class setCoordinates(lowX:Double, lowY:Double, uppX:Double, uppY:Double) extends Command
  case class joinNode(x:Double, y:Double,newNode:ActorRef) extends Command
  case class addNeighbor(c: Coordinate,node:ActorRef) extends Command
  case class removeNeighbor(c: Coordinate,node:ActorRef) extends Command
  case class AddedNode(added:String)
  case class findZone(x:Double, y:Double,newNode:ActorRef) extends Command
  case class responseCoordinates(c:Coordinate)
  case class updateNeighborCoords(node:ActorRef, c:Coordinate) extends Command
  case class storeData(key:String,value:String) extends Command
  case class addData(key:String, value:String) extends Command
  case class findData(data:String) extends Command
  case class leaveNode(selfRef:ActorRef) extends Command
  case class updateNeighborsNeighbors(selfRef:ActorRef) extends Command

  def props():Props = Props(new NodeActor())

  val entityIdExtractor:ExtractEntityId ={
    case Envelope(nodeActorId,command) => (nodeActorId.value.toString,command)
  }

  val shardIdExtractor:ExtractShardId ={
    case Envelope(nodeActorId, _) => Math.abs(nodeActorId.value.toString.hashCode % 1).toString
    case ShardRegion.StartEntity(entityId) =>Math.abs(entityId.hashCode % 1).toString
  }
}

class NodeActor extends Actor with ActorLogging {
  import NodeActor._
  import context._
  val config: Config = ConfigFactory.load()
  val maxx = config.getInt("Node.maxx")
  val maxy = config.getInt("Node.maxy")
  //val log = Logging(context.system,this)
  var flag = false
  var coordinate:Coordinate = _
  var neighbors = new mutable.HashMap[ActorRef,Coordinate]
  var nodeData = new mutable.HashMap[String,String]

  implicit val timeout: Timeout = Timeout(100.seconds)
  override def receive: Receive = {
    case setCoordinates(lowX:Double, lowY:Double, uppX:Double, uppY:Double)=>
      setcoordinates(lowX,lowY, uppX, uppY)
    case addData(key:String,value:String) =>
      log.info(s"Storing ${key}  in ${self.path.name} with coordinates ${coordinate}")
      nodeData.put(key,value)
    case joinNode(x:Double,y:Double,existing:ActorRef)=>{
      if(existing.path.name.equals(context.self.path.name)) {
        log.info("First node At join node")
        coordinate = new Coordinate(x,y,maxx,maxy)
        log.info("First Node Coordinates - {}", coordinate.toString)
      }
      else{
        log.info("Other node At join node")
        log.info(s"Existing Node  ${existing}")
        val selfnode = ClusterSharding(clusterActorSystem).shardRegion(context.self.path.name)
        log.info(s"New joining node ${selfnode}")
        val future = existing ? Envelope(entityIDMap.get(existing),findZone(x,y,selfnode))
        val result = Await.result(future,timeout.duration).asInstanceOf[responseCoordinates]
      }
      sender() ! AddedNode("added")
    }
    case findZone(x:Double, y:Double,newNode:ActorRef)=>{
      if(coordinate.contains(x,y))
      {
        log.info(s" ${newNode.path.name} lies in ${self.path.name}'s zone'")
        val new_coord = coordinate.splitZone()
        log.info(s"New node's new zone coordinates {}, {}, {}, {}",new_coord.lowerx,new_coord.lowery,new_coord.upperx,new_coord.uppery)
        newNode ! Envelope(entityIDMap.get(newNode),setCoordinates(new_coord.lowerx,new_coord.lowery,new_coord.upperx,new_coord.uppery))
        nodeData.foreach{
          node =>
            val dx = generateXCoordinate(node._1)
            val dy = generateYCoordinate(node._1)
            if(!coordinate.contains(dx,dy)){
              newNode ! Envelope(entityIDMap.get(newNode),addData(node._1,node._2))
              nodeData.remove(node._1)
            }
        }

        Thread.sleep(10)
        updateNeighbors(new_coord,newNode)
        Thread.sleep(10)
        sender() ! responseCoordinates(new_coord)
      }
      else{
        log.info(s"Finding closest neighbor of  ${newNode.path.name} in ${context.self.path.name}'s list'")
        val Neighbor = nearestNeighbors(x,y)
        val closestNeighbor = ClusterSharding(clusterActorSystem).shardRegion(Neighbor.path.name)
        Thread.sleep(10)
        (closestNeighbor ? Envelope(entityIDMap.get(closestNeighbor),findZone(x,y,newNode))).pipeTo(sender())
      }
    }


    case addNeighbor(c: Coordinate,node:ActorRef) =>
      log.info(s"Adding ${node.path.name} as a neighbor to ${self.path.name} addNeighbor case")
      val entnode = ClusterSharding(clusterActorSystem).shardRegion(node.path.name)
      neighbors.put(entnode,c)
    case removeNeighbor(c: Coordinate,node:ActorRef) =>
      log.info(s"Removing ${node.path.name} as a neighbor from ${self.path.name}")
      val entnode = ClusterSharding(clusterActorSystem).shardRegion(node.path.name)
      neighbors.remove(entnode)
    case updateNeighborCoords(node:ActorRef,c:Coordinate)=>
      {
        val entnode = ClusterSharding(clusterActorSystem).shardRegion(node.path.name)
        neighbors.update(entnode,c)
      }

    case storeData(key:String,value:String)=>{
      val x = generateXCoordinate(key)
      val y = generateYCoordinate(key)
      log.info(s"Coordinates generated for data ${key} are (x-${x} ,y-${y})")
      if(coordinate.contains(x,y)){
        log.info(s"Storing ${key} with (x-${x} ,y-${y}) in ${self.path.name} with coordinates ${coordinate}")
        nodeData.put(key,value)
      }
      else{
        val Neighbor = nearestNeighbors(x,y)
        Thread.sleep(10)
        val closestNeighbor = ClusterSharding(clusterActorSystem).shardRegion(Neighbor.path.name)
        (closestNeighbor ? Envelope(entityIDMap.get(closestNeighbor),storeData(key,value))).pipeTo(sender())
      }
    }

    case findData(key:String)=>{
      val x = generateXCoordinate(key)
      val y = generateYCoordinate(key)
      log.info(s"Coordinates requested data ${key} are (x-${x} ,y-${y})")
      if(coordinate.contains(x,y)){
        log.info(s"Finding ${key} with (x-${x} ,y-${y}) in ${context.self.path.name} with coordinates ${coordinate}")
        if(nodeData.contains(key))
          {
            log.info(s"Rating for movie ${key} is ${nodeData(key)} ")
          }
          else{
          log.info(s"Rating for movie '${key}' not found! ")
        }
      }
      else{
        val Neighbor = nearestNeighbors(x,y)
        Thread.sleep(10)
        val closestNeighbor = ClusterSharding(clusterActorSystem).shardRegion(Neighbor.path.name)
        (closestNeighbor ? Envelope(entityIDMap.get(closestNeighbor),findData(key))).pipeTo(sender())
      }
    }
    case leaveNode(selfNode:ActorRef) =>{
        if(neighbors.isEmpty)
          log.info(s"${selfNode.path.name} cannot leave the network, it's the only node")
        else{
          log.info(s"Finding best Neighbor for ${selfNode}")
          val Neighbor = getBestNeighbor()
          if(Neighbor != null){
            Bootstrap.removeNodeFromList(selfNode)
            val bestNeighbor = ClusterSharding(clusterActorSystem).shardRegion(Neighbor.path.name)
            log.info(s"Suitable neighbor for ${selfNode} is ${bestNeighbor}")
            log.info(s"Suitable neighbor for ${selfNode.path.name} is ${bestNeighbor.path.name}")
            nodeData.foreach{
              d =>
                log.info(s"Transferring ${d._1} from ${selfNode.path.name} to ${bestNeighbor.path.name}")
                bestNeighbor ! Envelope(entityIDMap.get(bestNeighbor),addData(d._1,d._2))
            }
            nodeData.clear()
            val mergedCoords = coordinate.mergeCoordinates(neighbors(bestNeighbor))
            log.info(s"Merged Coordinates of ${selfNode.path.name} and ${bestNeighbor.path.name} are ${mergedCoords}" )
            bestNeighbor ! Envelope(entityIDMap.get(bestNeighbor),setCoordinates(mergedCoords.lowerx,mergedCoords.lowery,mergedCoords.upperx,mergedCoords.uppery))
            neighbors.foreach{
              n =>
                if ((mergedCoords.isAdjacent(n._2, true) && mergedCoords.isWithinRange(n._2, false))
                  || (mergedCoords.isAdjacent(n._2, false) && mergedCoords.isWithinRange(n._2, true))){
                  n._1 ! Envelope(entityIDMap.get(n._1),addNeighbor(mergedCoords,bestNeighbor))
                  n._1 ! Envelope(entityIDMap.get(n._1),removeNeighbor(coordinate,selfNode))
                }
                else{
                  n._1 ! Envelope(entityIDMap.get(n._1),removeNeighbor(coordinate,bestNeighbor))
                  n._1 ! Envelope(entityIDMap.get(n._1),removeNeighbor(coordinate,selfNode))
                }
                selfNode ! Envelope(entityIDMap.get(selfNode),removeNeighbor(n._2,n._1))
                log.info(s"Update Best Neighbor ${bestNeighbor.path.name} of ${selfNode.path.name}neighbors'")
                bestNeighbor ! Envelope(entityIDMap.get(bestNeighbor), updateNeighborsNeighbors(bestNeighbor))
            }

          }
          else
            log.info(s"${selfNode.path.name} cannot leave the network, no suitable neighbor found for merging")
        }

    }
    case updateNeighborsNeighbors(selfRef:ActorRef)=>{
      log.info(s"Update Best Neighbor ${selfRef.path.name}'s neighbors'")
      neighbors.foreach{
        n=>
          n._1 ! Envelope(entityIDMap.get(n._1),updateNeighborCoords(selfRef,coordinate))
      }

    }
    case printState => printActorState()
    case _ => log.info("Default block")
  }
  def getBestNeighbor():ActorRef={
    //val suitableNeighbor= null
    log.info(s"Search ${context.self.path.name} 's neighbors for best neighbor")
    neighbors.foreach{
      n =>
        if(coordinate.isSameSize(n._2))
          return n._1
    }
    null
  }


  def updateNeighbors(newCoordinates:Coordinate,newNode:ActorRef):Unit={
    log.info(s"Adding ${newNode.path.name} as a neighbor to ${self.path.name} updateNeighbors Function")
    neighbors.put(newNode,newCoordinates)
    //neighbors.add(newCoordinates,newNode)
    newNode ! Envelope(entityIDMap.get(newNode),addNeighbor(coordinate,self))
    Thread.sleep(100)
    println(s"${self.path.name} filter ${neighbors.filter(_._1 != newNode).map(_._1.path.name)} list without ${newNode.path.name} ")
    if(neighbors.size>1) {
      neighbors.foreach {
        node =>
          log.info("Updating neighbors")
          if ((newCoordinates.isAdjacent(node._2, true) && newCoordinates.isWithinRange(node._2, false))
            || (newCoordinates.isAdjacent(node._2, false) && newCoordinates.isWithinRange(node._2, true))
              && (!newNode.equals(node._1))) {
            log.info(s"Adding ${newNode.path.name} as a neighbor to ${node._1.path.name}")
            node._1 ! Envelope(entityIDMap.get(node._1),addNeighbor(newCoordinates, newNode))
            newNode ! Envelope(entityIDMap.get(newNode),addNeighbor(node._2, node._1))
          }
          //check this condition
          if ((coordinate.isAdjacent(node._2, true) && coordinate.isWithinRange(node._2, false))
            || (coordinate.isAdjacent(node._2, false) && coordinate.isWithinRange(node._2, true))) {
            log.info(s"Updating ${node._1.path.name} as a neighbor from ${self.path.name}")
            node._1 ! Envelope(entityIDMap.get(node._1),updateNeighborCoords(self,coordinate))
           // node._1 ! addNeighbor(coordinate, self)
          }
          else {
            log.info(s"Removing ${node._1.path.name} as a neighbor from ${self.path.name}")
            neighbors.remove(node._1)
            node._1 ! Envelope(entityIDMap.get(node._1),removeNeighbor(coordinate, self))
          }
          Thread.sleep(100)
      }
    }

  }
  //Generate X coordinate for data string
  def generateXCoordinate(key:String):Double={
    var sum:Double = 0
    (0 until key.length by 2 ).map(i => sum+=key(i))
    sum % maxx
  }
  //Generate Y coordinate for data string
  def generateYCoordinate(key:String):Double={
    var sum:Double = 0
    (1 until key.length by 2 ).map(i => sum+=key(i))
    sum % maxy
  }
  //Find the nearest neighbor for a given point
  def nearestNeighbors(x:Double,y:Double):ActorRef={
    var minDistance = Double.MaxValue
    var temp:ActorRef = null
    neighbors.foreach{
      node =>
        if(node._2.contains(x,y))
          return node._1
        var dist = distance(x,y,node._2)
      if(dist<=minDistance){
        minDistance = dist
        temp = node._1
      }
    }
    //log.info(s"Nearest neighbor from ${self.path.name}'s list to ${newNode.path.name} is ${temp.path.name}")
    temp
  }
  //distance between two points 
  def distance(x:Double,y:Double,coordinate: Coordinate):Double={
    Math.sqrt(Math.pow((coordinate.centery - y ),2) + Math.pow((coordinate.centerx - x),2))
  }
  def setcoordinates(lowX:Double, lowY:Double, uppX:Double, uppY:Double):Unit={
    coordinate = new Coordinate(lowX,lowY,uppX,uppY)
    log.info(s"Setting ${self.path.name}'s coordinate -{${coordinate.toString}} ")
  }
  def printActorState():Unit={
    log.info(s"${self.path.name}'s coordinate -{${coordinate.toString}} ")
    Thread.sleep(10)
    //log.info("{}'s  Neighbors {}",self.path.name,neighbors)
    log.info("{}'s  Neighbors {}",self.path.name,neighbors.map(_._1.path.name))
    Thread.sleep(10)
    log.info(s"${context.self.path.name} 's data - ${nodeData} ")
  }


}
