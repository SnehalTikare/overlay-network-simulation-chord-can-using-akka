import akka.actor.{ActorRef, ActorSystem}

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.duration.DurationInt


class Server extends LazyLogging{
  //implicit val serverSystem: ActorSystem = ActorSystem("Server")

  //var bindings : Future[Http.ServerBinding] = _

  def start(shardRegion:ActorRef,serverActorSystem: ActorSystem, chordNodes : List[Int]): Route = {

    val requestPath = path("ons") {
      withRequestTimeout(60000.seconds)
      concat(
        get {
          parameter("key".as[String]) { key =>
            val response = Utils.DataUtils.getDataFromChord(shardRegion,serverActorSystem,chordNodes,key)
            logger.info("Response in server {}" , response)
            complete(StatusCodes.Accepted,response)
          }
        },
        post {
          parameter("key".as[String], "value".as[String]) { (key, value) =>
            val response = Utils.DataUtils.putDataToChord(shardRegion,serverActorSystem,chordNodes,key,value)
            complete(StatusCodes.Accepted)
          }
        }
      )
    }
    //bindings = Http().bindAndHandle(requestPath,"localhost",8080)
    //logger.info("Started Akka Http Server")
    requestPath
  }

//  def stop() :  Unit = {
//    implicit val executionContext: ExecutionContextExecutor = serverSystem.dispatcher
//    bindings.foreach(binding => {
//      binding.unbind()
//        .onComplete( binding => serverSystem.terminate())
//    })
//    logger.info("Stopped Akka Http Server")
//  }
}
