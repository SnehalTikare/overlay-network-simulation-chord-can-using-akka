package Utils

import Actors.ServerActor.{SearchNodeToWrite, getDataFromNode, sendValue}
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.Random

object DataUtils extends LazyLogging {

  val dataRecords = Utility.readCSV()
  def putDataToChord(serverActorSystem: ActorSystem, chordNodes: List[Int], key: String, value: String): Unit = {
    val keyHash = Utility.sha1(key)
    SimulationUtils.getRandomNode(serverActorSystem, chordNodes) ! SearchNodeToWrite(keyHash, key, value)
  }

  def getDataFromChord(serverActorSystem: ActorSystem, chordNodes: List[Int], key: String):
  String = {
    var response = ""
    logger.info("Trying to get rating for the requested movie")
    val keyHash = Utility.sha1(key)
    implicit val timeout: Timeout = Timeout(100.seconds)
    val future = SimulationUtils.getRandomNode(serverActorSystem, chordNodes) ? getDataFromNode(keyHash, key)
    val result = Await.result(future, timeout.duration).asInstanceOf[sendValue]
    if (result.value.equals("Movie not found"))
       response = "Requested movie doesn't have rating"
    else
       response = "IMDB rating for movie " + key + " is " + result.value
    logger.info("In data utils {} ",response)
    response
  }

  def getRandomData : (String,String) = {
    val random = new Random()
    val index = random.nextInt(dataRecords.size-1)
    dataRecords(index)
  }

}
