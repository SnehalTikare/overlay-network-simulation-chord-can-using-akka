import java.io.File

import Utils.{CommonUtils, DataUtils, SimulationUtils}
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import com.CAN.helper.{Bootstrap, Coordinate}
import com.CHORD.Actors.ServerActor
import com.typesafe.config.{Config, ConfigFactory}
import org.junit.Assert._
import org.junit.Test

class TestSuite {
  val config: Config = ConfigFactory.parseFile(new File("src/main/resources/application.conf"))
  Bootstrap

  @Test
  def testConfig: Unit = {
   assertEquals(config.getString("akka.actor.provider"), "cluster") }




//  @Test
// def testReadCSV : Unit = {
//    val data = DataUtils.readCSV()
//    assertNotNull(data)
//  }

 @Test
  def testGetRandom : Unit = {
    val number = CommonUtils.getRandom(0,10)
    assert(number <= 10)
    assert(number>=0)
  }

  @Test
  def testCoordinates : Unit = {
    val coordinates = new Coordinate(0,0,5,5)
    coordinates.setCenter()
    assertEquals(coordinates.centerx, 2.5,0)
    assertEquals(coordinates.centery, 2.5,0)
    assertTrue(coordinates.contains(1.2,3.4))
  }

  @Test
  def testMergeCoordinates : Unit = {
    val coordinate1 = new Coordinate(0,0,5,5)
    val coordinate2 = new Coordinate(5,5,10,10)
    val newCoordinate = coordinate1.mergeCoordinates(coordinate2)
    assertEquals(newCoordinate.lowerx,0,0)
    assertEquals(newCoordinate.lowery,0,0)
    assertEquals(newCoordinate.upperx,10,0)
    assertEquals(newCoordinate.uppery,10,0)
  }

  @Test
  def testBootstrap : Unit = {
    assertEquals(Bootstrap.nodeSize(),0)
    val randomCoordinate = Bootstrap.getRandomCoordinate()
    assertTrue(randomCoordinate < 10)
    assertTrue(randomCoordinate > 1)
  }


 @Test
  def testCheckRange : Unit = {
    assertTrue(CommonUtils.checkrange(true, 3,3,true,2))
    assertFalse(CommonUtils.checkrange(true,3,4,true,7))
  }

  @Test
  def testCreateChordRing : Unit = {
    val system = ActorSystem("ClusterActorSystem")
    val shardRegion: ActorRef = ClusterSharding(system).start(
      typeName = "ShardRegion",
      entityProps = Props[ServerActor](),
      settings = ClusterShardingSettings(system),
      extractEntityId = ServerActor.entityIdExtractor,
      extractShardId = ServerActor.shardIdExtractor)
    val chordNodeList = SimulationUtils.createChordRing(shardRegion,2)
    assertNotNull(chordNodeList)
    assertEquals(chordNodeList.size,2)
    system.terminate()
    Thread.sleep(100)
  }
}

