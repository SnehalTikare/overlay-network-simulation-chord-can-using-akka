import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._

import scala.concurrent.{ExecutionContextExecutor, Future}


class Server {
  implicit val serverSystem: ActorSystem = ActorSystem("Server")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  var bindings : Future[Http.ServerBinding] = _
  def start(): Unit = {
    val requestPath = path("ons") {
      concat(
        get {
          parameter("key".as[String]) { key =>
            //todo
            complete(StatusCodes.Accepted)
          }
        },
        post {
          parameter("key".as[String], "value".as[String]) { (key, value) =>
            //todo
            complete(StatusCodes.Accepted)
          }
        }
      )
    }
    bindings = Http().bindAndHandle(requestPath,"localhost",8080)
  }

  def stop() :  Unit = {
    implicit val executionContext: ExecutionContextExecutor = serverSystem.dispatcher
    bindings.foreach(binding => {
      binding.unbind()
        .onComplete( binding => serverSystem.terminate())
    })

  }
}
