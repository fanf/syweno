package com.normation.syweno.syncrepl

import scala.actors.Actor

/**
 * Interface to the actual service which handle the
 * synchronization. 
 * The service need a synchronization configuration, 
 * a cookie persister to track synchronization state, 
 * an interface to start/stop the synchronization and 
 * get its status, and a way to register listener
 * which will process synchronization messages. 
 */
trait SyncReplConsumer {
   
  def config : SyncreplConfiguration
  
  def cookiePersister : CookiePersister

  /**
   * The synchronization search manager. 
   * The manager understand SyncConnectionControlMessage:
   * - StartSync
   *     Start a new synchronization.
   *     Depending upon the configuration, the search will be persistent
   *     or a one time synchronization.
   * - StopSync 
   *     Stop a synchronization connection. Particularly interesting
   *     for a persistent one (a one time synchronization is stopped
   *     when finished). 
   */
  def syncSearchManager : Actor 

  /**
   * Retrieve the connection status 
   */
  
  def getSyncStatus:SyncConnectionState  
  /**
   * Register a new listener service that will
   * receive synchronization message.
   * @param entryProcessor
   */
  def registerSyncMessageListener(listener:SyncMessageListener) : Unit
  
}