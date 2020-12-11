package Utils

import com.CHORD.Actors.ServerActor.{Envelope, SearchNodeToWrite, getDataFromNode, sendValue}
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.Random

object DataUtils extends LazyLogging {

  val dataRecords = readCSV()
  val keyHashMapper= new mutable.HashMap[String,Int]

  def readCSV():List[(String, String)]={
    var dataCsv = List[(String, String)]()
    //Uncomment this when creating fat jar using sbt assembly
    //val bufferedSource = scala.io.Source.getClass.getResourceAsStream("/IMDB-Movie-Data.csv")
    //for (line <- scala.io.Source.fromInputStream(bufferedSource).getLines.drop(900)) {
    //Comment below two lines when creating fat jar using sbt assembly
    val bufferedSource =  io.Source.fromFile("src/main/resources/IMDB-Movie-Data.csv")
    for (line <- bufferedSource.getLines.drop(900)) {

      val cols = line.split(",").map(_.trim)
      dataCsv:+=(cols(0),cols(1))
    }
    bufferedSource.close
    logger.info("Number of movies and ratings available {} ", dataCsv.size )
    dataCsv
  }

  def putDataToChord(shardRegion:ActorRef,serverActorSystem: ActorSystem, chordNodes: List[Int], key: String, value: String): Unit = {
    //val keyHash = CommonUtils.sha1(key)
    val keyHash = chordNodes(new Random().nextInt(chordNodes.size))
    keyHashMapper.put(key,keyHash)
    val randomServerActor = chordNodes(new Random().nextInt(chordNodes.size))
    shardRegion ! Envelope(randomServerActor,SearchNodeToWrite(shardRegion,keyHash, key, value))
  }

  def getDataFromChord(shardRegion:ActorRef,serverActorSystem: ActorSystem, chordNodes: List[Int], key: String): String = {
    var response = ""
    logger.info("Trying to get rating for the requested movie")

    if(keyHashMapper.contains(key)){

    implicit val timeout: Timeout = Timeout(100.seconds)
    val randomServerActor = chordNodes(new Random().nextInt(chordNodes.size))
      val keyHash = keyHashMapper(key)
    val future = shardRegion ? Envelope(randomServerActor,getDataFromNode(shardRegion,keyHash,key))
    val result = Await.result(future, timeout.duration).asInstanceOf[sendValue]
    if (result.value.equals("Movie not found"))
       response = "Requested movie doesn't have rating"
    else
       response = "IMDB rating for movie " + key + " is " + result.value
    logger.info("In data utils {} ",response)
    response
    }
    else {
      response = s"Requested movie ${key} doesn't have rating"
      response
    }
  }

  def getRandomData : (String,String) = {
    val random = new Random()
    val index = random.nextInt(dataRecords.size-1)
    dataRecords(index)
  }

}
