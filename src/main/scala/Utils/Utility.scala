package Utils
import java.security.MessageDigest
import java.lang.Long

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Random

object Utility extends LazyLogging{

  val config: Config = ConfigFactory.load()
  val totalSize = config.getInt("count.zero.computers") //2^m
  val hashes = new mutable.HashSet[Int]()

  def sha1(input: String): Int = {
    //Message digest of input string is returned as array of bytes of size 20
    val hashVal = MessageDigest.getInstance("SHA1").digest(input.getBytes("UTF-8"))
    var sb: StringBuilder = new StringBuilder
    for (i <- 0 to 0) {
      sb = sb.append(String.format("%8s", Integer.toBinaryString(hashVal(i) & 0xFF)).replace(' ', '0'))
    }
    val hash = (Integer.parseInt(sb.toString(), 2) % totalSize).toInt

    (Integer.parseInt(sb.toString(), 2) % totalSize).toInt

  }

  def getRandom(min:Int, max:Int):Int={
    val rand = new Random
    val num = min + rand.nextInt((max - min)+1)
    num
  }


  def checkrange(beginInclude:Boolean,begin:Int, end:Int,endInclude:Boolean, id:Int):Boolean ={
    //logger.info("Begin =>" + begin + "End=> " + end + "Id => " + id)
    if(begin == end)
      true
    else if(begin < end){
      if(id == begin && beginInclude|| id == end && endInclude || (id > begin && id < end) )
        true
      else
        false
    }
    else{
      if(id == begin && beginInclude|| id == end && endInclude || (id > begin || id < end) )
        true
      else
        false
    }
  }
  def readCSV():List[(String, String)]={
    var dataCsv = List[(String, String)]()
    val bufferedSource = io.Source.fromFile("src/main/resources/IMDB-Movie-Data.csv")
    for (line <- bufferedSource.getLines.drop(1)) {

      val cols = line.split(",").map(_.trim)
      dataCsv:+=(cols(0),cols(1))
    }
    bufferedSource.close
    dataCsv
  }

  def generateRandomBoolean(): Boolean = {
    new Random().nextBoolean()
  }
}