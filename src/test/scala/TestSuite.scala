import java.io.File

import Utils.{CommonUtils, DataUtils, SimulationUtils}
import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.junit.Assert._
import org.junit.Test

class TestSuite {

  val config: Config = ConfigFactory.parseFile(new File("src/main/resources/application.conf"))

  @Test
  def testConfig: Unit = {
    assertEquals(config.getString("test"), "test")
  }

  @Test
  def testCreateActorSystem : Unit = {
    val actorSystem = SimulationUtils.createActorSystem("Test")
    assertEquals(actorSystem.getClass, ActorSystem("Expected").getClass)
  }

//  @Test
//  def testCreateChordRing : Unit = {
//    val chordNodes = SimulationUtils.createChordRing(ActorSystem("Test"),2)
//    assertNotNull(chordNodes)
//    assertEquals(chordNodes.size,2)
//  }

  @Test
  def testCreateUsers : Unit = {
    val users = SimulationUtils.createUsers(ActorSystem("Test"),2)
    assertNotNull(users)
    assertEquals(users.size,2)
  }


  @Test
  def testReadCSV : Unit = {
    val data = DataUtils.readCSV()
    assertNotNull(data)
  }

  @Test
  def testGetRandom : Unit = {
    val number = CommonUtils.getRandom(0,10)
    assert(number <= 10)
    assert(number>=0)
  }

  @Test
  def testCheckRange : Unit = {
    assertTrue(CommonUtils.checkrange(true, 3,3,true,2))
    assertFalse(CommonUtils.checkrange(true,3,4,true,7))
  }




}

