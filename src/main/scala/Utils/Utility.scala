package Utils
import java.security.MessageDigest

object Utility {
  def md5(value: String, numNodes: Int): Int = {
    //The 'm' represents the m in 2^m, where it denotes the number of entries in the finger table.
    val m: Int = Math.ceil(Math.log(numNodes) / Math.log(2)).toInt
    //Gets the bytes of the node Id which is passed as String and formats it as a hex value string.
    val x: String = MessageDigest.getInstance("MD5").digest(value.getBytes).map("%02x".format(_)).mkString
    val y = BigInt(x, 16).toString(2) //Uses base 2 to represent the numeric value
    val hashVal: Int = Integer.parseInt(y.substring(y.length() - m), 2)

    hashVal
  }
}
