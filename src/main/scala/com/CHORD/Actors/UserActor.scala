package com.CHORD.Actors

import java.util.concurrent.atomic.AtomicInteger

import com.CHORD.Actors.UserActor._
import akka.actor.Actor
import com.google.gson.JsonObject
import scalaj.http.{Http, HttpResponse}

class UserActor(userId: Int) extends Actor {
  val readreq = new AtomicInteger()
  val writereq = new AtomicInteger()

  def receive = {
    case Read(key: String) => {
      val response: HttpResponse[String] = Http("http://localhost:8080/ons").timeout(connTimeoutMs = 1000, readTimeoutMs = 10000).params(("key", key)).asString
      readreq.addAndGet(1)
      sender ! Response(response.body)
    }
    case Write(key: String, value: String) => {
      val response = Http("http://localhost:8080/ons").timeout(connTimeoutMs = 1000, readTimeoutMs = 10000).params(("key", key), ("value", value)).method("POST").asString
      writereq.addAndGet(1)
      sender ! Response(response.body)
    }
    case userGlobalState()=>{
      val userState = new JsonObject
      userState.addProperty("User", self.path.name)
      userState.addProperty("Write Requests: ",writereq)
      userState.addProperty("Read Requests: ", readreq)
      sender ! responseGlobalState(userState)
    }
  }
}

object UserActor {

  sealed case class Read(key: String)
  sealed case class Write(key: String, value: String)
  sealed case class Response(response : String)
  sealed case class userGlobalState()
  sealed case class responseGlobalState(details:JsonObject)


}
