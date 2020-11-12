package Actors

import Data.{FingerTableValue, ServerData}

import scala.collection.mutable

class ServerActor {
  private var fingerTable = new mutable.HashMap[Int, FingerTableValue]
  private var nodeId: Int = -1
  private var hashedNodeId : Int = -1
  private var data : ServerData = new ServerData
  private var hashedData : Int = -1
}

