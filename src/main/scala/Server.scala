import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}


class Server extends LazyLogging{
  implicit val serverSystem: ActorSystem = ActorSystem("Server")

  var bindings : Future[Http.ServerBinding] = _

  def start(serverActorSystem: ActorSystem, chordNodes : List[Int]): Unit = {

    val requestPath = path("ons") {
      withRequestTimeout(1000.seconds)
      concat(
        get {
          parameter("key".as[String]) { key =>
            val response = Utils.DataUtils.getDataFromChord(serverActorSystem,chordNodes,key)
            logger.info("Response in server {}" , response)
            complete(StatusCodes.Accepted,response)
          }
        },
        post {
          parameter("key".as[String], "value".as[String]) { (key, value) =>
            val response = Utils.DataUtils.putDataToChord(serverActorSystem,chordNodes,key,value)
            complete(StatusCodes.Accepted)
          }
        }
      )
    }
    bindings = Http().bindAndHandle(requestPath,"localhost",8080)
    logger.info("Started Akka Http Server")
  }

  def stop() :  Unit = {
    implicit val executionContext: ExecutionContextExecutor = serverSystem.dispatcher
    bindings.foreach(binding => {
      binding.unbind()
        .onComplete( binding => serverSystem.terminate())
    })
    logger.info("Stopped Akka Http Server")
  }
}
