package Actors

import java.util.concurrent.atomic.AtomicInteger

import Actors.UserActor._
import akka.actor.{Actor, Props}
import akka.cluster.sharding.ShardRegion
import akka.cluster.sharding.ShardRegion.{ExtractEntityId, ExtractShardId}
import akka.util.Helpers.Requiring
import com.google.gson.JsonObject
import scalaj.http.{Http, HttpResponse}
import Utils.SimulationUtils.{Command, Envelope, userActorIdMap}


class UserActor() extends Actor {
  val readreq = new AtomicInteger()
  val writereq = new AtomicInteger()

  def receive = {
    case Read(key: String) => {
      val response: HttpResponse[String] = Http("http://localhost:8080/ons").timeout(connTimeoutMs = 1000, readTimeoutMs = 10000).params(("key", key)).asString
      readreq.addAndGet(1)
      sender ! Envelope(userActorIdMap.get(sender),Response(response.body))
    }
    case Write(key: String, value: String) => {
      val response = Http("http://localhost:8080/ons").timeout(connTimeoutMs = 1000, readTimeoutMs = 10000).params(("key", key), ("value", value)).method("POST").asString
      writereq.addAndGet(1)
      sender ! Envelope(userActorIdMap.get(sender),Response(response.body))
    }
    case userGlobalState()=>{
      val userState = new JsonObject
      userState.addProperty("User", self.path.name)
      userState.addProperty("Write Requests: ",writereq)
      userState.addProperty("Read Requests: ", readreq)
      sender ! Envelope(userActorIdMap.get(sender),responseGlobalState(userState))
    }
  }
}

object UserActor {

  import Utils.SimulationUtils.Envelope

  def props():Props = Props(new UserActor())

  val entityIdExtractor:ExtractEntityId ={
    case Envelope(userActor,command) => (userActor.value.toString,command)
  }

  val shardIdExtractor:ExtractShardId ={
    case Envelope(userActorId, _) => Math.abs(userActorId.value.toString.hashCode % 1).toString
    case ShardRegion.StartEntity(entityId) =>Math.abs(entityId.hashCode % 1).toString
  }

  sealed case class Read(key: String) extends Command
  sealed case class Write(key: String, value: String) extends Command
  sealed case class Response(response : String) extends Command
  sealed case class userGlobalState() extends Command
  sealed case class responseGlobalState(details:JsonObject) extends Command
}
