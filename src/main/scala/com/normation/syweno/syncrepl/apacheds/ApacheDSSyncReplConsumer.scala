package com.normation.syweno.syncrepl
package apacheds

import org.slf4j.LoggerFactory
import org.apache.directory.ldap.client.api.LdapConnection
import org.apache.directory.ldap.client.api.future.SearchFuture
import org.apache.directory.ldap.client.api.message.{
  SearchIntermediateResponse,SearchRequest,SearchResponse,
  SearchResultDone,SearchResultEntry,SearchResultReference
}
import org.apache.directory.shared.ldap.message.AliasDerefMode
import org.apache.directory.shared.ldap.filter.SearchScope

import org.apache.directory.shared.ldap.codec.controls.replication.syncRequestValue.SyncRequestValueControl;
import org.apache.directory.shared.ldap.codec.controls.replication.syncDoneValue.{
  SyncDoneValueControl,SyncDoneValueControlDecoder
}
import org.apache.directory.shared.ldap.codec.controls.replication.syncInfoValue.{
  SyncInfoValueControl,SyncInfoValueControlDecoder
}
import org.apache.directory.shared.ldap.codec.controls.replication.syncStateValue.{
  SyncStateValueControl,SyncStateValueControlDecoder
}
import org.apache.directory.ldap.client.api.message.BindResponse
import org.apache.directory.shared.ldap.message.ResultCodeEnum
import org.apache.directory.shared.ldap.message.control.replication.SynchronizationModeEnum

import net.liftweb.common._
import scala.util.control.Breaks._
import org.apache.directory.shared.ldap.util.StringTools
import scala.collection.JavaConversions._
import org.apache.directory.shared.ldap.message.control.replication.SyncStateTypeEnum
import net.liftweb.util.ControlHelpers.tryo
import scala.actors.Actor
import java.util.concurrent.{TimeUnit, TimeoutException}



class ApacheDSSyncReplConsumer(
  val config : SyncreplConfiguration,
  val cookiePersister : CookiePersister
) extends SyncReplConsumer {

  private var syncMessageListener:List[SyncMessageListener] = Nil

  private val LOG = LoggerFactory.getLogger(classOf[ApacheDSSyncReplConsumer])
  /** the decoder for syncinfovalue control */
  private val decoder = new SyncInfoValueControlDecoder()
  private val syncDoneControlDecoder = new SyncDoneValueControlDecoder()
  private val syncStateControlDecoder = new SyncStateValueControlDecoder()

  private val connectionProvider = new ApacheDSLdapConnectionProvider(config.providerConfig)
  
  private val syncActor = new SyncActor
    

  /**
   * The synchronization search manager. 
   * The manager understand SyncConnectionControlMessage:
   * - StartSync
   *     Start a new synchronization.
   *     Depending upon the configuration, the search will be persistent
   *     or a one time synchronization.
   * - StopSync 
   *     Stop a synchronization connection. Particulary intersting
   *     for a persistent one (a one time synchronization is stopped
   *     when finished). 
   */
  lazy val syncSearchManager : Actor = {
    syncActor.start
    syncActor
  }

  def registerSyncMessageListener(listener:SyncMessageListener) : Unit = {
    syncMessageListener = listener :: syncMessageListener
  }
  
  def getSyncStatus:SyncConnectionState = syncActor.getSyncStatus
  
  //// implementation details ////
  
  
  /** the syncrequest control */
  private def newSyncReqControl(cookie:Option[Array[Byte]]) = {
    val sr = new SyncRequestValueControl
    if ( config.isRefreshPersist ) {
      sr.setMode( SynchronizationModeEnum.REFRESH_AND_PERSIST )
    } else {
      sr.setMode( SynchronizationModeEnum.REFRESH_ONLY )
    }
    
    /* man slapo-syncprov:
     * Specify that the overlay should honor the reloadHint flag in the Sync Control. 
     * In OpenLDAP releases 2.3.11 and earlier the syncrepl consumer did not properly 
     * set this flag, so the  overlay  must  ignore  it.  This  option should  be set 
     * TRUE when working with newer releases that properly support this flag. It must 
     * be set TRUE when using the accesslog overlay for delta-based syncrepl replication 
     * support.  The default is FALSE.
     */
    sr.setReloadHint( true )
    cookie foreach { c => sr.setCookie(c) }
    sr
  }

  /** the search request with control */
  private def newSearchRequest : SearchRequest = {
    val sr = new SearchRequest()
    sr.setBaseDn( config.baseDn )
    sr.setFilter( config.filter )
    sr.setSizeLimit( config.searchSizeLimit )
    sr.setTimeLimit( config.searchTimeout )
    // the only valid values are NEVER_DEREF_ALIASES and DEREF_FINDING_BASE_OBJ
    sr.setDerefAliases( AliasDerefMode.NEVER_DEREF_ALIASES )
    sr.setScope( SearchScope.getSearchScope( config.searchScope ) )
    sr.setTypesOnly( false )
    sr.addAttributes( config.attributes:_* )
    
    sr.add( newSyncReqControl(cookiePersister.readCookie(config.replicaId) ) )
    
    sr
  }


  /** utility method to persist the cookie */
  private def saveCookie(cookie:Array[Byte]):Unit = {
    if ( cookie != null ) {
      cookiePersister.storeCookie(config.replicaId, cookie)
      LOG.debug( "assigning the cookie from sync state value control: {}",
        StringTools.utf8ToString( cookie ) )
    }
  }

  /**
   * The SyncActor class, which defined all the client
   * behaviour in the synchronization.
   */
  private class SyncActor extends Actor {
    /*
     * Implementation details.
     * We have two elements here:
     * - a thread dedicated to the connection, which start the
     *   LDAP search and get back results.
     *   It does so as soon as created and until it is
     *   given a stopSyncPulling order or a SearchResultDone happen.
     *   Each time a response is gotten, it is forwarded to 
     *   the SyncActor
     * - a SyncActor that process both search result message
     *   accordingly to Syncrepl protocol and SyncConnectionControlMessage
     *   to start / stop the connection thread. 
     */
    
    syncActor => 
    
    //we internally managed a seperated thread for the Connection
    
    private class SyncThread extends Thread {
      @volatile var stopSync = false
      setDaemon( true )
      
      def stopSyncPulling : Unit = {
        stopSync = true
        this.interrupt()
      }
      
      override def run : Unit = {
        syncActor ! SyncStarted
        val searchRequest = newSearchRequest
        connectionProvider.connection.foreach { connection => 
          val sf = connection.searchAsync( searchRequest )
          breakable {
            try {
              while(!stopSync) {
                if(sf.isCancelled) break
                else sf.get match {
                  case srd : SearchResultDone => syncActor ! srd ; break //special case
                  case x => syncActor ! x
                }
              }
            } catch {
              case e:InterruptedException => break
            }
          }
        }
        syncActor ! SyncStopped
      }
    }
    
    private val logger = LoggerFactory.getLogger(classOf[SyncActor])

    //we give the connection handler its own thread
    private var connectionThread : Option[SyncThread] = None
    private var syncState : SyncConnectionState = SyncStopped
    
    private def stopSync : Unit = {
      /*
       * Stop the connection if still up, clean state
       */
      connectionThread.foreach { _.stopSyncPulling }
      connectionProvider.disconnect
    }
    
    def getSyncStatus:SyncConnectionState = syncState
    
    override def act = { while(true) { receive {
      case SyncStarted => 
        logger.debug("Synchro started !")
        syncState  = SyncStarted
        
      case SyncStopped => 
        logger.debug("Synchro correctly stoped")
        connectionThread = None
        syncState = SyncStopped
        
      case StartSync => 
        /*
         * start synchro : bring up the connection, use the configuration to
         * init the search request, etc
         * If we already have a valid search future, does nothing
         */
        logger.debug("Receive a StartSync message, try to establish connection")
        (syncState, connectionThread) match {
          case (SyncStopped,None) => //ok 
            val ct = new SyncThread
            ct.start
            connectionThread = Some(ct)
          case (SyncStarted,Some(ct)) if(ct.isAlive) => //ok
            logger.debug("Sync connection thread already running")
          case (state, ct) => //unsound state
             logger.debug("Sync thread in unexpected state, stopping")
             ct.foreach( _.stopSync )
        }
      
      case StopSync => 
        logger.debug("StopSync message received")
        stopSync
      
      case srd : SearchResultDone => 
        handleSearchDone( srd )
        this ! StopSync
      case sre : SearchResultEntry => 
        handleSearchResult( sre , syncMessageListener )
      case srr : SearchResultReference => 
        handleSearchReference( srr )
      case sir : SearchIntermediateResponse => 
        handleSyncInfo( sir , syncMessageListener )
      case x => 
        LOG.warn("Ignoring unknown response type: {}", x)
    } } }
  }

  private def handleSearchReference(result:SearchResultReference) : Unit = { 
    LOG.error( "!!!!!!!!!!!!!!!!! TODO handle SearchReference messages !!!!!!!!!!!!!!!!" )
    LOG.debug("{}",result)
  }

  /**
   * Handle a "search done" sync message. 
   * Mostly persist the cookie for further search requests. 
   */
  private def handleSearchDone(result:SearchResultDone) : Unit = { 
    LOG.debug( "///////////////// handleSearchDone //////////////////" )

    try {
      syncDoneControlDecoder.decode( 
          result.getControl( SyncDoneValueControl.CONTROL_OID ).getValue, 
          new SyncDoneValueControl
      ) match {
        case syncDoneCtrl : SyncDoneValueControl => 
          LOG.debug( "refreshDeletes: " + syncDoneCtrl.isRefreshDeletes )
          
          //refreshDeletes = syncDoneCtrl.isRefreshDeletes();
          saveCookie(syncDoneCtrl.getCookie)

          result.getLdapResult.getResultCode match {
            case ResultCodeEnum.E_SYNC_REFRESH_REQUIRED =>
              /*
                  The server may return e-syncRefreshRequired
                  result code on the initial content poll if it is safe to do so when
                  it is unable to perform the operation due to various reasons.
                  reloadHint is set to FALSE in the SearchRequest Message requesting
                  the initial content poll.
                  
                  TODO: Q: The default value is already FALSE then why should this be set to FALSE
                  and how that will help in achieving convergence? should the cookie be reset to null?)
               */
               cookiePersister.removeCookie(config.replicaId)
            case x if(x!= ResultCodeEnum.SUCCESS ) =>
              // log the error and handle it appropriately
               LOG.warn( "sync operation was not successful, received result code {}", x )
            case ResultCodeEnum.SUCCESS =>
               LOG.debug("sync operation succeed")
          }
      }
      LOG.debug( "//////////////// END handleSearchDone//////////////////////" )    
    }
  }
  
  private def handleSearchResult(result:SearchResultEntry, entryProcessors:List[SyncMessageListener]) : Unit = {
    LOG.debug( "------------- starting handleSearchResult ------------" );
    try {
      syncStateControlDecoder.decode( 
          result.getControl( SyncStateValueControl.CONTROL_OID ).getValue,
          new SyncStateValueControl 
      ) match {
        case syncStateCtrl: SyncStateValueControl =>
          val (dn,uuid, attributes) = (
              result.getEntry.getDn.toString, 
              StringTools.uuidToString( syncStateCtrl.getEntryUUID() ),
              result.getEntry.iterator.map { entryAttribute => 
                //transform each entry attribute into a pair of (id, list(values)) where values are string values
                (entryAttribute.getId, entryAttribute.getAll.toList.map(_.getString))
              } toMap
          )
          
          LOG.debug( "entryUUID = {}",  uuid)
          LOG.debug( "state name {}", syncStateCtrl.getSyncStateType.name )
          
          syncStateCtrl.getSyncStateType match {
            case SyncStateTypeEnum.ADD     => entryProcessors.foreach { _.sync(Add(dn,uuid,attributes)) }
            case SyncStateTypeEnum.MODIFY  => entryProcessors.foreach { _.sync(Modify(dn,uuid,attributes)) }
            case SyncStateTypeEnum.DELETE  => entryProcessors.foreach { _.sync(Delete(dn,uuid)) }
            case SyncStateTypeEnum.PRESENT => entryProcessors.foreach { _.sync(Present(dn,uuid)) }
          }
          saveCookie( syncStateCtrl.getCookie )
          
        case x => 
          LOG.error("Get something not expected: {}",x)
      }
    } catch {
      case e:Exception => LOG.error( e.getMessage(), e )
    }
    LOG.debug( "------------- Ending handleSearchResult ------------" );
  }
  
  private def handleSyncInfo(result:SearchIntermediateResponse, entryProcessors:List[SyncMessageListener]) : Unit = {
    try {
      LOG.debug( "............... inside handleSyncInfo ..............." )

      decoder.decode( result.getResponseValue, null ) match {
        case syncInfoValue:SyncInfoValueControl =>
          
          val uuidList = syncInfoValue.getSyncUUIDs match {
            case null => Nil
            case l => l.map { uuid => StringTools.uuidToString( uuid ) }.toList
          }

          if(LOG.isDebugEnabled) {
            LOG.debug( "refreshDeletes: " + syncInfoValue.isRefreshDeletes )
            LOG.debug( "refreshDone: " + syncInfoValue.isRefreshDone )
            
            val opType = if(syncInfoValue.isRefreshDeletes) "DEL" else "PRES"

            for(uuid <- uuidList) {
              LOG.info( "{} uuid: {}", opType, uuid )
            }
          }

          // if refreshDeletes set to true then delete all the entries with entryUUID
          // present in the syncIdSet 
          if(!uuidList.isEmpty) {
            if(syncInfoValue.isRefreshDeletes) {
              entryProcessors.foreach { _.sync(MassDelete(uuidList)) }
            } else {
              entryProcessors.foreach { _.sync(MassPresent(uuidList)) }
            }
          }
          
          saveCookie( syncInfoValue.getCookie )
        case x =>
          LOG.error("Found an unknow control: {}",x)
      }
    } catch { 
      case de:Exception =>
        LOG.error( "Failed to handle syncinfo message" );
        de.printStackTrace();
    }
    LOG.debug( ".................... END handleSyncInfo ..............." );
  }
}