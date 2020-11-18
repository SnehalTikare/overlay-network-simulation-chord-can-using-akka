package Utils

import Actors.ServerActor.{ChordGlobalState, Test, joinRing}
import Actors.{ServerActor, UserActor}
import Actors.UserActor.{Read, Response, Write}
import akka.actor.{ActorRef, ActorSelection, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object SimulationUtils extends LazyLogging {
  val config: Config = ConfigFactory.load()
  val numUsers: Int = config.getInt("count.users")
  val actorRefHashMap = new mutable.HashMap[ActorRef, Int]()
  val actorNodes = new Array[ActorRef](config.getInt("count.nodes"))
  val random = scala.util.Random

  def createActorSystem(systemName:String):ActorSystem ={
    ActorSystem(systemName)
  }

  def createChordRing(system:ActorSystem, numNodes : Int):List[Int]={
    val NodesHashList = new Array[Int](numNodes)
    val nodeId = random.nextInt(Integer.MAX_VALUE)
    val initialNodeHash  = CommonUtils.sha1(nodeId.toString)
    NodesHashList(0) = initialNodeHash
    val initialNode = system.actorOf(Props(new ServerActor(initialNodeHash)), name = initialNodeHash.toString)
    actorNodes(0) = initialNode
    actorRefHashMap.put(initialNode,initialNodeHash)
    logger.info("First Node id => " + 0 + "\t\tHashedNodeId => " + initialNodeHash)
    for(x <- 1 until numNodes){
      var nodeId = random.nextInt(Integer.MAX_VALUE)
      val nextnodeHash = CommonUtils.sha1(nodeId.toString)
      actorNodes(x) = system.actorOf(Props(new ServerActor(nextnodeHash)), name =nextnodeHash.toString)
      NodesHashList(x) = nextnodeHash
      actorRefHashMap.put(actorNodes(x),nextnodeHash)
      logger.info("Node id => " + x + "\t\tHashedNodeId => " + nextnodeHash)
      Thread.sleep(2)
      implicit val timeout: Timeout = Timeout(10.seconds)
      val future = actorNodes(x) ? joinRing(initialNode,initialNodeHash)
      val result = Await.result(future, timeout.duration)
      logger.info("Nodes successfully updated after node "+  nextnodeHash + " join "+ result)
      Thread.sleep(100)
    }
    NodesHashList.toList
  }

  def createUsers(system:ActorSystem, numUsers : Int):List[String]={
    val config: Config = ConfigFactory.load()
    val Users = new Array[String](numUsers)
    for( i <- 0 until numUsers){
      Users(i) = "user"+i
      system.actorOf(Props(new UserActor(i)),Users(i))
    }
    Users.toList
  }

  def getRandomNode(serverActorSystem:ActorSystem, nlist:List[Int]):ActorSelection={
    val i = CommonUtils.getRandom(0, nlist.size -1)
    val node = nlist(i)
    serverActorSystem.actorSelection("akka://ServerActorSystem/user/" + node)
  }

  def getRandomUser(userActorSystem:ActorSystem, nlist:List[String]):ActorSelection={
    val i = CommonUtils.getRandom(0, nlist.size -1)
    val node = nlist(i)
    userActorSystem.actorSelection("akka://UserActorSystem/user/" + node)
  }

  def generateRequests(users : List[String], userActorSystem : ActorSystem) : Unit = {
    implicit val timeout: Timeout = Timeout(100.seconds)
    val numberOfRequests = CommonUtils.getRandom(config.getInt("requests.minimum"),
      config.getInt("requests.maximum"))
      for (i <- 0 to numberOfRequests) {
        val randomUser = getRandomUser(userActorSystem, users)
        val randomData = DataUtils.getRandomData
        val isWriteRequest = CommonUtils.generateRandomBoolean()
        logger.info("Request  - {} - isWrite - {} - data - {}", i, isWriteRequest,randomData)
        if(isWriteRequest){
          val futureResponse = randomUser ? Write(randomData._1,randomData._2)
          val responseString = Await.result(futureResponse, timeout.duration).asInstanceOf[Response]
        }
        else{
          val futureResponse = randomUser ? Read(randomData._1)
          val responseString = Await.result(futureResponse, timeout.duration).asInstanceOf[Response]
          logger.info(responseString.toString)
        }
      }
  }

  def terminateSystem(serverActorSystem:ActorSystem,userActorSystem:ActorSystem):Unit={
    logger.info("Terminating Server Actor System and User Actor System")
    serverActorSystem.terminate()
    userActorSystem.terminate()
  }

  def getGlobalState():Unit={
    implicit val timeout: Timeout = Timeout(10.seconds)
    for(node <- actorNodes) {
      val future = node ? ChordGlobalState(actorRefHashMap)
      val result = Await.result(future, timeout.duration)
      println(result)
    }
  }



}
