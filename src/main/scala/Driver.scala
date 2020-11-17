import java.lang.Math.random

import Actors.ServerActor.{ChordGlobalState, Test, joinRing, updateHashedNodeId}
import akka.actor._
import Actors.{ServerActor, SupervisorActor, UserActor}
import Utils.Utility
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.{LazyLogging, Logger}

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt



object Driver extends LazyLogging {

  val config: Config = ConfigFactory.load()
  val numNodes: Int = config.getInt("count.nodes")
  val numUsers: Int = config.getInt("count.users")
  val actorRefHashMap = new mutable.HashMap[ActorRef, Int]()
  val actorNodes = new Array[ActorRef](numNodes)
  val random = scala.util.Random
  def createActorSystem(systemName:String):ActorSystem ={
    ActorSystem(systemName)
  }
  def createChordRing(system:ActorSystem):List[Int]={
    val NodesHashList = new Array[Int](numNodes)
    var nodeId = random.nextInt(Integer.MAX_VALUE)
    val initialNodeHash  = Utility.sha1(nodeId.toString)
    NodesHashList(0) = initialNodeHash
    val initialNode = system.actorOf(Props(new ServerActor(initialNodeHash)), name = "Node" + 0 + "-in-chord-ring")
    actorNodes(0) = initialNode
    actorRefHashMap.put(initialNode,initialNodeHash)
    logger.info("First Node id => " + 0 + "\t\tHashedNodeId => " + initialNodeHash)
    for(x <- 1 until numNodes){
      var nodeId = random.nextInt(Integer.MAX_VALUE)
      val nextnodeHash = Utility.sha1(nodeId.toString)
      actorNodes(x) = system.actorOf(Props(new ServerActor(nextnodeHash)), name = "Node" + x + "-in-chord-ring")
      NodesHashList(x) = nextnodeHash
      actorRefHashMap.put(actorNodes(x),nextnodeHash)
      logger.info("Node id => " + x + "\t\tHashedNodeId => " + nextnodeHash)
      Thread.sleep(2)
      implicit val timeout: Timeout = Timeout(10.seconds)
      val future = actorNodes(x) ? joinRing(initialNode,initialNodeHash)
      val result = Await.result(future, timeout.duration)
      logger.info("Nodes successfully updated after node "+  nextnodeHash + " join "+ result)
      actorNodes(0) ! Test()
      Thread.sleep(100)
    }
    NodesHashList.toList
  }

  def createUsers(system:ActorSystem):List[String]={
    val Users = new Array[String](numUsers)
    for( i <- 0 until numUsers){
      Users(i) = "user"+i
      system.actorOf(Props(new UserActor(i)),Users(i))
    }
    Users.toList
  }
  def TerminateSystem(server:ActorSystem,user:ActorSystem):Unit={
    logger.info("Terminating Server and User Actor System")
    server.terminate()
    user.terminate()
  }

  def getGlobalState():Unit={
    implicit val timeout: Timeout = Timeout(10.seconds)
    for(node <- actorNodes) {
      val future = node ? ChordGlobalState(actorRefHashMap)
      val result = Await.result(future, timeout.duration)
      println(result)
    }
  }
  def main(args: Array[String]): Unit = {
    //An ActorSystem is the initial entry point into Akka.
    logger.info("Creating Server Actor System")
    val serverActorSystem= createActorSystem("ServerActorSystem")
    logger.info("Adding nodes to Chord ring")
    val chordNodes = createChordRing(serverActorSystem)
    Thread.sleep(1000)
    logger.info("Creating User Actor System")
    val userActorSystem = createActorSystem("UserActorSystem")
    logger.info("Creating Users")
    val users = createUsers(userActorSystem)
    logger.info("Reading Data from CSV")
    val data = Utility.readCSV()
//    data.foreach(x =>
//    println(x._1,x._2))
    Thread.sleep(1000)
    getGlobalState()
    TerminateSystem(serverActorSystem,userActorSystem)


  }
}