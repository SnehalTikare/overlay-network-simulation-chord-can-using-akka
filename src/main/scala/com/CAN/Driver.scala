package com.CAN

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.pattern.ask
import akka.util.Timeout
import com.CAN.Actors.NodeActor
import com.CAN.Actors.NodeActor._
import com.CAN.helper.Bootstrap
import com.CAN.helper.Bootstrap.entityIDMap
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

//import akka.http.impl.util.StreamUtils.OnlyRunInGraphInterpreterContext.system

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object Driver extends LazyLogging{

  var result: AddedNode = _
  val config: Config = ConfigFactory.load()
  val minx = config.getInt("Node.minx")
  val miny = config.getInt("Node.miny")
  val number_of_nodes = config.getInt("Node.count")
  implicit val timeout: Timeout = Timeout(100.seconds)

  val clusterActorSystem= createActorSystem("ClusterActorSystem")

  def createActorSystem(systemName:String):ActorSystem ={
    ActorSystem(systemName)
  }
  def addNodeToNetwork(Id:Int):Unit={
    val node :ActorRef=ClusterSharding(clusterActorSystem).shardRegion(Id.toString)

    if(Bootstrap.nodeSize()==0)
      {
        logger.info(s"First node in CAN - {${node.path.name}}")
        val future = node ? Envelope(Id,joinNode(minx,miny,node))
        result = Await.result(future, timeout.duration).asInstanceOf[AddedNode]
        Bootstrap.addNodeToList(node)
        logger.info("Added node to list")
      }
    else{
      //val randomNode = Bootstrap.getRandom()
      val randomNumber =  Bootstrap.getRandomNumber()
      val randomNode:ActorRef=ClusterSharding(clusterActorSystem).shardRegion(randomNumber.toString)
      logger.info(s"RandomNode chosen ${randomNode}")
      val randomX = Bootstrap.getRandomCoordinate()
      val randomY = Bootstrap.getRandomCoordinate()
      logger.info(s"New node's random coordinates are (x-${randomX}, y-${randomY})")
      logger.info(s"Received ${node.path.name}'s join request to ${randomNode.path.name}")
      val future = node ? Envelope(entityIDMap.get(node),joinNode(randomX,randomY,randomNode))
      result = Await.result(future, timeout.duration).asInstanceOf[AddedNode]
      logger.info(node.path.name,result.added)
      Bootstrap.addNodeToList(node)
    }
    Thread.sleep(1000)
  }

  def globalState():Unit={
      Bootstrap.nodeList.foreach {
        node => node ! Envelope(entityIDMap.get(node),printState)
      }
  }
  def addDataToNode(key:String,value:String):Unit={
    val randomNumber =  Bootstrap.getRandomNumber()
    val randomNode:ActorRef=ClusterSharding(clusterActorSystem).shardRegion(randomNumber.toString)
    randomNode ! Envelope(entityIDMap.get(randomNode),storeData(key,value))
  }

  def findMovieRating(key:String):Unit={
    val randomNumber =  Bootstrap.getRandomNumber()
    val randomNode:ActorRef=ClusterSharding(clusterActorSystem).shardRegion(randomNumber.toString)
    randomNode ! Envelope(entityIDMap.get(randomNode),findData(key))
  }


  def main(args: Array[String]): Unit = {
    logger.info("Creating Server Actor System")
    val config: Config = ConfigFactory.load()
    val numNodes = config.getInt("Node.count")
    //AkkaManagement(clusterActorSystem).start()
   /* for(i <- 1 to numNodes){
      var node = createNode(clusterActorSystem,i)
      addNodeToNetwork(node)
      //Thread.sleep(1000)
    }
    Thread.sleep(3000)
    globalState()
    Thread.sleep(3000)
    addDataToNode("Inception","5")
    Thread.sleep(1000)
    findMovieRating("Inception")
    findMovieRating("The Holiday")
    clusterActorSystem.terminate()*/
   for (i <- 1 to numNodes) {
     val nodeRegion: ActorRef = ClusterSharding(clusterActorSystem).start(
       typeName = i.toString,
       entityProps = Props[NodeActor](),
       settings = ClusterShardingSettings(clusterActorSystem),
       extractEntityId = NodeActor.entityIdExtractor,
       extractShardId = NodeActor.shardIdExtractor)
       entityIDMap.put(nodeRegion,i)
       addNodeToNetwork(i)
    }
    Thread.sleep(1000)
    globalState()
    addDataToNode("Inception","7.5")
    addDataToNode("Sherlock","9.2")
//    findMovieRating("Inception")
//    findMovieRating("The Holiday")
//    findMovieRating("Sherlock")
//    Thread.sleep(2000)
//    globalState()
//    Thread.sleep(2000)
//    val dummyNode = ClusterSharding(clusterActorSystem).shardRegion("5")
//    dummyNode ! Envelope(entityIDMap.get(dummyNode), leaveNode(dummyNode))
//
//    Thread.sleep(5000)
//    globalState()
//    Thread.sleep(5000)
//    findMovieRating("Inception")
//    findMovieRating("The Holiday")
//    findMovieRating("Sherlock")
    //    val node = createNode(clusterActorSystem,1)
//    addNodeToNetwork(node)
//    val node2 = createNode(clusterActorSystem,2)
//    Thread.sleep(100)
//    addNodeToNetwork(node2)
//    Thread.sleep(100)


   /* AkkaManagement(clusterActorSystem).start()
    val orders = ClusterSharding(clusterActorSystem).start(
      "CAN-Nodes",
      NodeActor.props(),
      ClusterShardingSettings(clusterActorSystem),
      NodeActor.entityIdExtractor,
      NodeActor.shardIdExtractor
    )*/

  }
}
