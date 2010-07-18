package com.normation.syncrepl
package unboundid

import org.junit.Assert._
import org.junit.{Test,AfterClass}

import scala.actors.Actor

class TestSyncreplConsumer {

  val providerConfig = ProviderConfiguration(
    "localhost",
    389,
    "cn=manager, dc=example, dc=org",
    "secret"
  )
  
  val config = SyncreplConfiguration(
    providerConfig,
    42,
    "ou=Servers,dc=example,dc=org",
    "(objectClass=*)",
    Seq("objectClass", "linuxDistributionVersion"),
    isRefreshPersist = true
  )
  
    
  @Test def dummy() {}

  //@Test 
  def launchAndRestart {
    val consumer = new UnboundIDSyncReplConsumer(config,new DefaultCookiePersister)
    consumer.registerSyncMessageListener(DummyEntryProcessor)
    val syncActor = consumer.syncSearchManager
    syncActor ! StartSync
    
    Thread.sleep(1 * 1000)
    println("*** stop the actor ***")
    syncActor ! StopSync
    
    //Thread.sleep(1000)
    println("*** restart ****")
    syncActor ! StartSync
    Thread.sleep(1 * 1000)
    println("*** stop the actor ***")
    syncActor ! StopSync
    Thread.sleep(1 * 1000)
  }
  
  //@Test 
  def logModification {
    val consumer = new UnboundIDSyncReplConsumer(config,new DefaultCookiePersister)
    consumer.registerSyncMessageListener(ConsoleLogSyncListener)
    val syncActor = consumer.syncSearchManager
    syncActor ! StartSync
    
    Thread.sleep(1 * 1000)
    println("*** stop the actor ***")
    syncActor ! StopSync
    Thread.sleep(2 * 1000)
  }
  
}
