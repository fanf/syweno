package bootstrap.liftweb

import com.normation.syweno.syncrepl._
import apacheds._
import unboundid._

import net.liftweb.util.Props
import net.liftweb.common._

/**
 * Syncrepl configuration. 
 * 
 * Of course, in a real application, that should go in some
 * sort of configuration file, like a properties one. 
 */

//LDAP server connection


//syncrepl search configuratio - only override non default values
object SyncReplConfig {
  
  Props.requireOrDie(
    "ldap.provider.bind.dn",
    "ldap.provider.bind.pwd",
    "syncrepl.replicaid",
    "syncrepl.search.basedn"
  )

  val provider = ProviderConfiguration(
      Props.get("ldap.provider.host").openOr("localhost"),
      Props.getInt("ldap.provider.port").openOr(389),
      Props.get("ldap.provider.bind.dn").open_!,
      Props.get("ldap.provider.bind.pwd").open_!
  )

  val sync = SyncreplConfiguration(
    provider,
    Props.getInt("syncrepl.replicaid").open_!,
    Props.get("syncrepl.search.basedn").open_!,
    Props.get("syncrepl.search.filter", "(objectClass=*)"),
    Props.get("syncrepl.search.attributes").map(_.split(",").toSeq.map(_.trim)).openOr(Seq()),
    Props.getInt("syncrepl.search.scope", 1),
    Props.getBool("syncrepl.refresh.persist", true),
    Props.getInt("syncrepl.search.sizelimit", 0),
    Props.getInt("syncrepl.search.timeout", 0),
    Props.getBool("syncrepl.reloadhint", true)
  )  
}  

/**
 * The actual Syncrepl consumer. After that declaration, it is up and ready to:
 * - accept "entries processors" with ScalaSyncReplConsumer#registerProcessor
 * - start / stop a synchronization by sending SyncConnectionControlMessage message to
 *   the sync manager Actor, for ex: SyncreplConsumer.syncSearchManager ! StartSync
 */
object ApacheDSSyncreplConsumer extends ApacheDSSyncReplConsumer(
  SyncReplConfig.sync,
  new DefaultCookiePersister("%s/syncreplCookies/apacheds".format(System.getProperty("java.io.tmpdir")))
)

object UnboundIDSyncreplConsumer extends UnboundIDSyncReplConsumer(
  SyncReplConfig.sync,
  new DefaultCookiePersister("%s/syncreplCookies/unboundid".format(System.getProperty("java.io.tmpdir")))
)
  