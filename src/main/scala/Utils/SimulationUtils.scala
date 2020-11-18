package Utils

import Actors.UserActor
import Actors.UserActor.{Read, Response, Write}
import akka.actor.{ActorSelection, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object SimulationUtils extends LazyLogging {
  val config: Config = ConfigFactory.load()

  def getRandomNode(serverActorSystem:ActorSystem, nlist:List[Int]):ActorSelection={
    val i = Utility.getRandom(0, nlist.size -1)
    val node = nlist(i)
    serverActorSystem.actorSelection("akka://ServerActorSystem/user/" + node)
  }

  def getRandomUser(userActorSystem:ActorSystem, nlist:List[String]):ActorSelection={
    val i = Utility.getRandom(0, nlist.size -1)
    val node = nlist(i)
    userActorSystem.actorSelection("akka://UserActorSystem/user/" + node)
  }


  def createUsers(system:ActorSystem):List[String]={
    val config: Config = ConfigFactory.load()
    val numUsers: Int = config.getInt("count.users")
    val Users = new Array[String](numUsers)
    for( i <- 0 until numUsers){
      Users(i) = "user"+i
      system.actorOf(Props(new UserActor(i)),Users(i))
    }
    Users.toList
  }

  def generateRequests(users : List[String], userActorSystem : ActorSystem) : Unit = {
    implicit val timeout: Timeout = Timeout(100.seconds)
    val numberOfRequests = Utility.getRandom(config.getInt("requests.minimum"),
      config.getInt("requests.maximum"))
      for (i <- 0 to numberOfRequests) {
        val randomUser = getRandomUser(userActorSystem, users)
        val randomData = DataUtils.getRandomData
        val isWriteRequest = Utility.generateRandomBoolean()
        logger.info("Request  - {} - isWrite - {} - data - {}", i, isWriteRequest,randomData)
        if(isWriteRequest){
          val futureResponse = randomUser ? Write(randomData._1,randomData._2)
          val responseString = Await.result(futureResponse, timeout.duration).asInstanceOf[Response]

        }
        else{
          val futureResponse = randomUser ? Read(randomData._1)
          val responseString = Await.result(futureResponse, timeout.duration).asInstanceOf[Response]
          print(responseString)
        }
      }


  }


}
