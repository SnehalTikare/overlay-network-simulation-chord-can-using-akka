package Actors

import java.util.concurrent.atomic.AtomicInteger

import Actors.UserActor._
import akka.actor.Actor
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

  }
}

object UserActor {

  sealed case class Read(key: String)
  sealed case class Write(key: String, value: String)
  sealed case class Response(response : String)

}
