
import Utils.SimulationUtils.config
import Utils.{CommonUtils, SimulationUtils}
import akka.actor.ActorRef
import akka.http.scaladsl.Http
import com.CHORD.Actors.ServerActor.{Envelope, TestSharding}
import com.typesafe.scalalogging.{LazyLogging, Logger}

import scala.concurrent.ExecutionContextExecutor


object ChordDriver extends LazyLogging {

  val numNodes: Int = config.getInt("count.nodes")
  val numUsers: Int = config.getInt("count.users")


  def main(args: Array[String]): Unit = {

    logger.info("Creating Server Actor System")
    implicit val serverActorSystem= SimulationUtils.createActorSystem("ClusterActorSystem")

    logger.info("Creating Shard Region")
    val shardRegion = SimulationUtils.createShardRegion(serverActorSystem)
    implicit val execContext:ExecutionContextExecutor=serverActorSystem.dispatcher
    //logger.info("Adding nodes to Chord ring")
    val chordNodes = SimulationUtils.createChordRing(shardRegion, numNodes)

      Thread.sleep(1000)
//
      val server = new Server()
      val serverObj = server.start(shardRegion,serverActorSystem, chordNodes)
      val bindFuture = Http().bindAndHandle(serverObj,"localhost")

//
//    logger.info("Creating User Actor System")
//    val userActorSystem = SimulationUtils.createActorSystem("UserActorSystem")
//
//    logger.info("Creating Users")
//    val users = Utils.SimulationUtils.createUsers(userActorSystem, numUsers)
    SimulationUtils.getGlobalState(shardRegion:ActorRef)

    Thread.sleep(1000)

    Utils.SimulationUtils.generateRequests()
//
   Thread.sleep(1000)

    SimulationUtils.getGlobalState(shardRegion:ActorRef)

    Thread.sleep(1000)

    //SimulationUtils.getUserGlobalState()

    Thread.sleep(100)

    //server.stop()
    bindFuture
      .flatMap(_.unbind())
      .onComplete(_=>serverActorSystem.terminate())
    //SimulationUtils.terminateSystem(serverActorSystem,userActorSystem)
  }
}