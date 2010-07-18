package com.normation.syncrepl

/**
 * Interface of the service in charge to actually
 * do something with synchronization messages.
 * 
 * Just register implementation to a
 * ScalaSyncReplConsumer. The consumer
 * will forward received message 
 * accordingly
 */
trait SyncMessageListener {
  def sync(message:SyncEntryMessage) : Unit
}