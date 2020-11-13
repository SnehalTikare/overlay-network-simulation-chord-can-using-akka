package Utils
import java.security.MessageDigest

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

object Utility extends LazyLogging{
  val config: Config = ConfigFactory.load()
  val limit = config.getInt("count.zero.end-value")

  def sha1(input: String): Int = {
    //Message digest of input string is returned as array of bytes of size 20
    val hashVal = MessageDigest.getInstance("SHA1").digest(input.getBytes())
    var sb: StringBuilder = new StringBuilder
    for (i <- 0 to limit) {
      sb = sb.append(String.format("%8s", Integer.toBinaryString(hashVal(i) & 0xFF)).replace(' ', '0'))
    }
    Integer.parseInt(sb.toString(), 2)
  }
}