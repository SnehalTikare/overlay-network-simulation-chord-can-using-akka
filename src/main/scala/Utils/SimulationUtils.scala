package Utils

import Actors.ServerActor._
import Actors.{ServerActor, UserActor}
import Actors.UserActor._
import akka.actor.{ActorRef, ActorSelection, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.google.gson.{GsonBuilder, JsonArray}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import java.io.File

import org.apache.commons.io.FileUtils



object SimulationUtils extends LazyLogging {
  val config: Config = ConfigFactory.load()
  val numUsers: Int = config.getInt("count.users")
  val actorRefHashMap = new mutable.HashMap[ActorRef, Int]()
  val actorNodes = new Array[ActorRef](config.getInt("count.nodes"))
  val random = scala.util.Random
  val Users = new Array[String](numUsers)
  val userActors = new Array[ActorRef](numUsers)

  /**
   *
   * @param systemName :Name of the actor system to be created
   * @return created Actor system
   */
  def createActorSystem(systemName:String):ActorSystem ={
    ActorSystem(systemName)
  }

  /**
   * Function to create nodes in the Chord ring.
   * @param system Actor System (Server Actor System)
   * @param numNodes (Number of nodes to be created in the chord
   * @return List of nodes created
   */

  def createChordRing(system:ActorSystem, numNodes : Int):List[Int]={
    val NodesHashList = new Array[Int](numNodes)
    //val nodeId = random.nextInt(Integer.MAX_VALUE)
    val initialnodeId = "Node_0"
    val initialNodeHash  = CommonUtils.sha1(initialnodeId.toString) //Get the hash of the node
    NodesHashList(0) = initialNodeHash
    val initialNode = system.actorOf(Props(new ServerActor(initialNodeHash)), name = initialNodeHash.toString)
    actorNodes(0) = initialNode
    actorRefHashMap.put(initialNode,initialNodeHash)
    logger.info("First Node id => " + 0 + "\t\tHashedNodeId => " + initialNodeHash)
    for(x <- 1 until numNodes){
      //var nodeId = random.nextInt(Integer.MAX_VALUE)
      val nodeId = "Node_"+x
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

  /**
   * Create User Actors
   * @param system - User actor system
   * @param numUsers - Number of users to be created
   * @return - List of users created
   */
  def createUsers(system:ActorSystem, numUsers : Int):List[String]={
    val config: Config = ConfigFactory.load()
    for( i <- 0 until numUsers){
      Users(i) = "user"+i
      userActors(i) = system.actorOf(Props(new UserActor(i)),Users(i))
    }
    Users.toList
  }

  /**
   * @param serverActorSystem
   * @param nlist
   * @return Returns a randomly chosen node from list of created nodes
   */
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

  /**
   * The method that generates write and read requests to add/fetch data to/from node
   * @param users - List of users who generate the requests
   * @param userActorSystem
   *
   */
  def generateRequests(users : List[String], userActorSystem : ActorSystem) : Unit = {
    implicit val timeout: Timeout = Timeout(100.seconds)
    val numberOfRequests = CommonUtils.getRandom(config.getInt("requests.minimum"),
      config.getInt("requests.maximum"))
      for (i <- 0 to numberOfRequests) {
        val randomUser = getRandomUser(userActorSystem, users) //Get a random user
        val randomData = DataUtils.getRandomData
        val isWriteRequest = CommonUtils.generateRandomBoolean()
        logger.info("Request  - {} - isWrite - {} - data - {}", i, isWriteRequest,randomData)
        Thread.sleep(10000)
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

  /**
   * stop the actor system
   * @param serverActorSystem - Server Actor System
   * @param userActorSystem  - User Actor System
   */
  def terminateSystem(serverActorSystem:ActorSystem,userActorSystem:ActorSystem):Unit={
    logger.info("Terminating Server Actor System and User Actor System")
    serverActorSystem.terminate()
    userActorSystem.terminate()
  }

  /**
   * Get the global state of all the nodes in the chord.
   */
  def getGlobalState():Unit={
    implicit val timeout: Timeout = Timeout(10.seconds)
    val gson = new GsonBuilder().setPrettyPrinting().create()
    val nodestate = new JsonArray()
    for(node <- actorNodes) {
      val future = node ? ChordGlobalState(actorRefHashMap)
      val result = Await.result(future, timeout.duration).asInstanceOf[GlobalState]
      nodestate.add(result.details)
    }
    writeToFile(gson.toJson(nodestate),"ChordGlobalState")
  }

  /**
   * Get the number of read and write requests generated by the each user
   */
  def getUserGlobalState():Unit={
    implicit val timeout: Timeout = Timeout(10.seconds)
    val gson = new GsonBuilder().setPrettyPrinting().create()
    val userstate = new JsonArray()
    for(user <- userActors){
      val future = user ? userGlobalState()
      val result =  Await.result(future, timeout.duration).asInstanceOf[responseGlobalState]
      userstate.add(result.details)
    }
    writeToFile(gson.toJson(userstate),"UserGlobalState")
  }

  /**
   * Dump the user and node's state to a json file
   * @param data - user/node's state
   * @param datapath - filename
   */
  def writeToFile(data:String,datapath:String):Unit={
    val path=s"output/$datapath.json"
    logger.info("Writing {}to the file",datapath)
    FileUtils.write(new File(path), data, "UTF-8")
  }

}
