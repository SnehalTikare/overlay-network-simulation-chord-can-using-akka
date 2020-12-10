object MainDriver {

  def main(args: Array[String]): Unit = {
    print("Select your option.\n1. Chord\n2. CAN\n")
    val option = scala.io.StdIn.readLine()
    if(option == 1){
      ChordDriver.main(null)
    }
    else{
      com.CAN.Driver.main(null)
    }
  }
}
