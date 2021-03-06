package Utils
import java.security.MessageDigest
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable
import scala.util.Random

object CommonUtils extends LazyLogging{

  val config: Config = ConfigFactory.load()
  val totalSize = config.getInt("count.zero.computers") //2^m
  val hashes = new mutable.HashSet[Int]()

  /**
   * Generate the hash of the input key
   * @param input - key (node/data)
   * @return hash value of the key
   */
  def sha1(input: String): Int = {
    //Message digest of input string is returned as array of bytes of size 20
    val hashVal = MessageDigest.getInstance("SHA1").digest(input.getBytes("UTF-8"))
    var sb: StringBuilder = new StringBuilder
    for (i <- 0 to 0) {
      sb = sb.append(String.format("%8s", Integer.toBinaryString(hashVal(i) & 0xFF)).replace(' ', '0'))
    }
    Integer.parseInt(sb.toString(), 2)
    //val hash = (Integer.parseInt(sb.toString(), 2) % totalSize).toInt

    //(Integer.parseInt(sb.toString(), 2) % totalSize).toInt
  }

  /**
   * Method to generate random number in the given range
   * @param min - lower range
   * @param max - upper range
   * @return random number between [min, max)
   */
  def getRandom(min:Int, max:Int):Int={
    val rand = new Random
    val num = min + rand.nextInt((max - min)+1)
    num
  }

  /**
   * Function to check the if a number lies within a given range
   * @return
   */

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

  /**
   * Generate a random boolean value
   * @return
   */
  def generateRandomBoolean(): Boolean = {
    new Random().nextBoolean()
  }
}