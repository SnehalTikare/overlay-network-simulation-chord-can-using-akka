package Utils
import java.security.MessageDigest

object Utility {
  def md5(value: String): String = {
    MessageDigest.getInstance("MD5").digest(value.getBytes).toString
  }
}
