package com.CAN.helper

import java.util

import akka.actor.ActorRef
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable
import scala.util.Random

object Bootstrap extends LazyLogging {
  val seed = 10
  val nodeList = new mutable.HashSet[ActorRef]
  var entityIDMap = new util.HashMap[ActorRef,Int]
  def addNodeToList(node:ActorRef):Unit={
    logger.info(s"Adding ${node.path.name} to Bootstrap's node List")
    nodeList.+=(node)
  }
  def removeNodeFromList(node:ActorRef):Unit={
    nodeList.-=(node)
  }
//  def getRandom(): ActorRef = {
//    val randomNode = nodeList(new Random(seed).nextInt(nodeList.size))
//    randomNode
//  }
  def nodeSize():Int={
     nodeList.size
  }
  def getRandomNumber():Int={
    val rand = new Random(seed)
    val randomNumber = 1 + rand.nextInt((nodeList.size - 1)+1)
    randomNumber
  }
  def getRandomCoordinate():Double={
    val min = 1
    val max = 9
    val count = (max - min)/seed
    //(((count * Math.random)* seed) + min)
    (min + (Math.random * ((max - min) + 1))).toDouble
  }

}
