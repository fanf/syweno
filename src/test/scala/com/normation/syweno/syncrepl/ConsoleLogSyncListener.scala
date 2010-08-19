package com.normation.syweno.syncrepl


/**
 * dummy listener, does nothing
 *
 */
object DummyEntryProcessor extends SyncMessageListener {
  def sync(message:SyncEntryMessage) {}
}



/**
 * A listener that process entry synchro message
 * only with logging then to console.
 */
object ConsoleLogSyncListener extends SyncMessageListener {
  override def sync(message:SyncEntryMessage) : Unit = {
    message match {
      case Add(dn,uuid, attrs) =>
        println("**")
        println("SYNC ADD %s".format(dn))
        println("**")
      case Modify(dn,uuid, attrs) => 
        println("**")
        println("SYNC MOD %s".format(dn))
        println("**")
      case Delete(dn,uuid) =>
        println("**")
        println("SYNC DEL %s".format(dn))
        println("**")
      case Present(dn,uuid) => 
        println("**")
        println("SYNC PRES %s".format(dn))
        println("**")
      case MassDelete(uuids) =>
        println("**")
        uuids.foreach { dn => println("SYNC DEL UUID %s".format(dn)) }
        println("**")
      case MassPresent(uuids) =>
        println("**")
        uuids.foreach { dn => println("SYNC PRESENT UUID %s".format(dn)) }
        println("**")
    }
  }
}
