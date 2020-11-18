
import Utils.SimulationUtils.config
import Utils.{CommonUtils, SimulationUtils}

import com.typesafe.scalalogging.{LazyLogging, Logger}


object Driver extends LazyLogging {

  val numNodes: Int = config.getInt("count.nodes")
  val numUsers: Int = config.getInt("count.users")

  def main(args: Array[String]): Unit = {

    logger.info("Creating Server Actor System")
    val serverActorSystem= SimulationUtils.createActorSystem("ServerActorSystem")

    logger.info("Adding nodes to Chord ring")
    val chordNodes = SimulationUtils.createChordRing(serverActorSystem, numNodes)

    Thread.sleep(1000)

    val server = new Server
    server.start(serverActorSystem, chordNodes)

    logger.info("Creating User Actor System")
    val userActorSystem = SimulationUtils.createActorSystem("UserActorSystem")

    logger.info("Creating Users")
    val users = Utils.SimulationUtils.createUsers(userActorSystem, numUsers)

    Thread.sleep(1000)

    Utils.SimulationUtils.generateRequests(users, userActorSystem)

    Thread.sleep(1000)

    SimulationUtils.getGlobalState()

    Thread.sleep(1000)

    SimulationUtils.getUserGlobalState()

    Thread.sleep(100)

    server.stop()

    SimulationUtils.terminateSystem(serverActorSystem,userActorSystem)
  }
}