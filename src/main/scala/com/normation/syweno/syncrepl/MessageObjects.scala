package com.normation.syweno.syncrepl

/*
 * File on which common message object
 * are defined
 */


/**
 * Message used to control the state of the synchronization. 
 */
sealed trait SyncConnectionControlMessage
case object StartSync   extends SyncConnectionControlMessage
case object StopSync    extends SyncConnectionControlMessage
//case object RestartSync extends SyncConnectionControlMessage

/**
 * Messages describing the state of the connection
 *
 */
sealed trait SyncConnectionState
case object SyncStarted extends SyncConnectionState
case object SyncStopped extends SyncConnectionState

/**
 * Synchronization messages send to SyncMessageListeners
 *
 */
sealed trait SyncEntryMessage
case class Add(dn:String, uuid:String, attributes:Map[String,List[String]])    extends SyncEntryMessage
case class Modify(dn:String, uuid:String, attributes:Map[String,List[String]]) extends SyncEntryMessage
case class Delete(dn:String, uuid:String)                                      extends SyncEntryMessage
case class Present(dn:String, uuid:String)                                     extends SyncEntryMessage
case class MassDelete(uuids:List[String])                                      extends SyncEntryMessage
case class MassPresent(uuids:List[String])                                     extends SyncEntryMessage
