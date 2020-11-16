package Utils
import java.security.MessageDigest
import java.lang.Long

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

object Utility extends LazyLogging{
  val config: Config = ConfigFactory.load()
  val totalSize = config.getInt("count.zero.computers") //2^m

  def sha1(input: String): Int = {
    //Message digest of input string is returned as array of bytes of size 20
    val hashVal = MessageDigest.getInstance("SHA1").digest(input.getBytes("UTF-8"))
    var sb: StringBuilder = new StringBuilder
    for (i <- 0 to 2) {
      sb = sb.append(String.format("%8s", Integer.toBinaryString(hashVal(i) & 0xFF)).replace(' ', '0'))
    }
    (Integer.parseInt(sb.toString(), 2) % totalSize).toInt

  }

  def checkrange(begin:Int, end:Int, id:Int):Boolean ={
    //logger.info("Begin =>" + begin + "End=> " + end + "Id => " + id)
    if(begin < end){
      id>=begin && id < end
    }
    else if(begin == end)
      true
    else{
      id>=begin || id < end
    }
  }
}