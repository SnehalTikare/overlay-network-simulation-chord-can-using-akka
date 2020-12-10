package com.CAN.helper

import com.typesafe.scalalogging.LazyLogging


class Coordinate(var lowerx:Double, var lowery:Double, var upperx:Double, var uppery:Double) extends LazyLogging {
  var centerx = (lowerx + upperx) / 2
  var centery = (lowery + uppery ) / 2

  def setCenter():Unit={
    centerx = (lowerx + upperx) / 2
    centery = (lowery + uppery ) / 2
  }
  def updateCoord( lx:Double, ly:Double,  ux:Double,  uy:Double):Unit={
    lowerx = lx
    lowery = ly
    upperx = ux
    uppery = uy
    centerx = (lowerx + upperx) / 2
    centery = (lowery + uppery ) / 2
  }

//  def contains(x:Double, y:Double):Boolean={
//    if(x>=lowerx && x<=upperx && y>=lowery && y<=uppery)
//      true
//    else false
//  }
  def contains(x:Double, y:Double):Boolean={
    if(lowerx<=x && x<upperx && lowery<=y && y<uppery)
      true
    else
      false
  }
  def isSameSize(c: Coordinate):Boolean={
    if (((upperx - lowerx) == (c.upperx - c.lowerx))
      && ((uppery - lowery) == (c.uppery - c.lowery)))
       true
    else
      false
  }
  def mergeCoordinates(c:Coordinate):Coordinate={
    if (c.lowerx >= upperx)
      c.lowerx = lowerx
    else if (c.upperx <= lowerx)
      c.upperx = upperx
    if (c.lowery >= uppery)
      c.lowery = lowery
    else if (c.uppery <= lowery)
      c.uppery = uppery
      c

  }


  def splitZone():Coordinate={
    if((upperx - lowerx) >= (uppery-lowery))
    {
      logger.info("Splitting the zone vertically")
      val old_upperX = upperx
      upperx = (lowerx+upperx)/2//left zone
      centerx = (lowerx + upperx) / 2
      centery = (lowery + uppery ) / 2
      //this.setCenter()
      logger.info(s"Existing node's new zone coordinates {}, {}, {}, {}",lowerx,lowery,upperx,uppery)
      new Coordinate(upperx,lowery,old_upperX,uppery)//right zone
    }
    else
    {
      logger.info("Splitting the zone horizontally")
      val old_upperY = uppery
      uppery = (lowery+uppery)/2 //lower zone
      centerx = (lowerx + upperx) / 2
      centery = (lowery + uppery ) / 2
      //this.setCenter()
      logger.info(s"Existing node's new zone coordinates {}, {}, {}, {}",lowerx,lowery,upperx,uppery)
      new Coordinate(lowerx,uppery,upperx,old_upperY)//upper zone
    }
  }
  def isAdjacent(c: Coordinate, checkX:Boolean): Boolean ={
    if(checkX){
        if(lowerx == c.upperx || upperx==c.lowerx)
          true
        else
          false
    }
    else{
      if(lowery == c.uppery || uppery == c.lowery)
        true
      else
        false
    }
  }
  def isWithinRange(c:Coordinate, checkX:Boolean):Boolean={
    if(checkX){
      if((lowerx <= c.lowerx && upperx >= c.upperx) || (lowerx >= c.lowerx && upperx <= c.upperx))
        true
      else
        false
    }
    else{
      if((lowery <= c.lowery && uppery >= c.uppery) || (lowery >= c.lowery && uppery <= c.uppery))
        true
      else
        false
    }
  }

    override def toString: String = {
    ("\nlowerX:" + lowerx + "\tupperX: " + upperx
      + "\nlowerY: " + lowery + "\tupperY: " + uppery + "\n" +
      "\tcenteryX: " + centerx + "\tcenteryY: " + centery + "\n")
    }
}
