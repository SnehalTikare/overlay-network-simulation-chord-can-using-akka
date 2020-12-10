object MainDriver {

  def main(args: Array[String]): Unit = {
    print("Select your option.\n1. Chord\n2. CAN\n")
    val option = scala.io.StdIn.readInt()
    if(option == 1){
      ChordDriver.run(null)
    }
    else{
      com.CAN.Driver.run(null)
    }
  }
}
