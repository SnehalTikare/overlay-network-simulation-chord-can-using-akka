package Utils

import com.CHORD.Actors.ServerActor._
import com.CHORD.Actors.{ServerActor, UserActor}
import com.CHORD.Actors.UserActor._
import akka.actor.{ActorRef, ActorSelection, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.google.gson.{GsonBuilder, JsonArray, JsonObject}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import org.apache.commons.io.FileUtils
import scalaj.http.{Http, HttpResponse}

import scala.collection.mutable.ListBuffer

object SimulationUtils extends LazyLogging {
  val config: Config = ConfigFactory.load()
  val numUsers: Int = config.getInt("count.users")
  val numComputers =  config.getInt("count.zero.computers")
  val actorRefHashMap = new mutable.HashMap[ActorRef, Int]()
  val actorNodes = new Array[ActorRef](config.getInt("count.nodes"))
  val random = scala.util.Random
  val Users = new Array[String](numUsers)
  val userActors = new Array[ActorRef](numUsers)
  val nodeIDList = new ListBuffer[Int]
  var readreq:Int = 0
  var writereq:Int = 0
  implicit val timeout: Timeout = Timeout(10.seconds)
  /**
   *
   * @param systemName :Name of the actor system to be created
   * @return created Actor system
   */
  def createActorSystem(systemName:String):ActorSystem ={
    ActorSystem(systemName)
  }
  def createShardRegion(system:ActorSystem):ActorRef={
      val ShardRegion: ActorRef = ClusterSharding(system).start(
        typeName = "ShardRegion",
        entityProps = Props[ServerActor](),
        settings = ClusterShardingSettings(system),
        extractEntityId = ServerActor.entityIdExtractor,
        extractShardId = ServerActor.shardIdExtractor)
    ShardRegion
  }

  /**
   * Function to create nodes in the Chord ring.
   * @param numNodes (Number of nodes to be created in the chord
   * @return List of nodes created
   */

  def createChordRing(shardRegion:ActorRef,numNodes : Int):List[Int]={
    val NodesHashList = new Array[Int](numNodes)
    //val nodeId = random.nextInt(Integer.MAX_VALUE)
    val initialnodeId = 0
    NodesHashList(0) = initialnodeId
    nodeIDList+=initialnodeId
    val future = shardRegion ? Envelope(initialnodeId,joinRing(shardRegion,initialnodeId))
    val result = Await.result(future, timeout.duration)
      logger.info("First Node id => " + 0 + "\t\tHashedNodeId => " + initialnodeId)

    while(nodeIDList.size<numNodes){
      val nodeId = random.nextInt(numComputers)
      if(!nodeIDList.contains(nodeId))
        {
          val future = shardRegion ? Envelope(nodeId,joinRing(shardRegion, initialnodeId))
          val result = Await.result(future, timeout.duration)
        logger.info("Node id => " + nodeId + "\t\tHashedNodeId => " + nodeId)
          nodeIDList+=nodeId
        Thread.sleep(100)
      }
    }
    nodeIDList.toList
  }

  /**
   * Create User com.CHORD.Actors
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
   */
  def generateRequests() : Unit = {
    implicit val timeout: Timeout = Timeout(1000.seconds)
    val numberOfRequests = CommonUtils.getRandom(config.getInt("requests.minimum"),
      config.getInt("requests.maximum"))
      for (i <- 0 to numberOfRequests) {
        //val randomUser = getRandomUser(userActorSystem, users) //Get a random user
        val randomData = DataUtils.getRandomData
        val isWriteRequest = CommonUtils.generateRandomBoolean()
        val key = randomData._1
        val value = randomData._2
        logger.info("Request  - {} - isWrite - {} - data - {}", i, isWriteRequest,randomData)
        Thread.sleep(10000)
        if(isWriteRequest){
//          val futureResponse = randomUser ? Write(randomData._1,randomData._2)
//          val responseString = Await.result(futureResponse, timeout.duration).asInstanceOf[Response]
          val response = Http("http://localhost:8000/ons").timeout(connTimeoutMs = 1000, readTimeoutMs = 10000).params(("key", key), ("value", value)).method("POST").asString
          writereq+=1
        }
        else{
//          val futureResponse = randomUser ? Read(randomData._1)
//          val responseString = Await.result(futureResponse, timeout.duration).asInstanceOf[Response]
          val response: HttpResponse[String] = Http("http://localhost:8000/ons").timeout(connTimeoutMs = 1000, readTimeoutMs = 10000).params(("key", key)).asString
          readreq+=1
          logger.info(response.toString)
        }
      }
  }
  def globalChordRequestState():Unit={
    val gson = new GsonBuilder().setPrettyPrinting().create()
    val userState = new JsonObject
    userState.addProperty("Total Write Requests: ",writereq)
    userState.addProperty("Total Read Requests: ", readreq)
    userState.addProperty("Average Read Requests: ", readreq/(readreq+writereq))
    userState.addProperty("Average Write Requests: ", readreq/(readreq+writereq))
    writeToFile(gson.toJson(userState),"ChordRequestGlobalState")
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
  def getGlobalState(shardRegion:ActorRef):Unit={
    implicit val timeout: Timeout = Timeout(10.seconds)
    val gson = new GsonBuilder().setPrettyPrinting().create()
    val nodestate = new JsonArray()
    for(node <- nodeIDList) {
      val future = (shardRegion ? Envelope(node,ChordGlobalState(actorRefHashMap)))
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
