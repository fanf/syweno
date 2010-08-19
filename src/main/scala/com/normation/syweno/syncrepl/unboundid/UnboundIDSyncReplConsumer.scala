package com.normation.syweno.syncrepl
package unboundid

import org.slf4j.LoggerFactory
import com.unboundid.ldap.sdk.{
  LDAPConnection,SearchRequest,SearchResultReference,ResultCode,
  AsyncSearchResultListener,DereferencePolicy,SearchResultEntry,
  Control,SearchScope,SearchResult,AsyncRequestID,IntermediateResponseListener,
  IntermediateResponse
}
import com.unboundid.ldap.sdk.controls.{
  ContentSyncDoneControl,ContentSyncInfoIntermediateResponse,ContentSyncInfoType,
  ContentSyncRequestControl,ContentSyncRequestMode,ContentSyncState,ContentSyncStateControl
}
import com.unboundid.asn1.ASN1OctetString

import net.liftweb.common._
import scala.util.control.Breaks._
import org.apache.directory.shared.ldap.util.StringTools
import scala.collection.JavaConversions._
import org.apache.directory.shared.ldap.message.control.replication.SyncStateTypeEnum
import net.liftweb.util.ControlHelpers.tryo
import scala.actors.Actor
import java.util.concurrent.{TimeUnit, TimeoutException}



class UnboundIDSyncReplConsumer(
  val config : SyncreplConfiguration,
  val cookiePersister : CookiePersister
) extends SyncReplConsumer {

  private var syncMessageListener:List[SyncMessageListener] = Nil

  private val LOG = LoggerFactory.getLogger(classOf[UnboundIDSyncReplConsumer])

  private val connectionProvider = new UnboundIDLdapConnectionProvider(config.providerConfig)
  
  private val syncActor = new SyncActor
    
  
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
  private def newSyncReqControl(cookie:Option[ASN1OctetString]) = {
    new ContentSyncRequestControl(
      //mode : persist connection or one shot
      if ( config.isRefreshPersist ) {
        ContentSyncRequestMode.REFRESH_AND_PERSIST
      } else {
        ContentSyncRequestMode.REFRESH_ONLY
      },
      //state cookie
      cookie.getOrElse(new ASN1OctetString),
      config.reloadHint
    )
  }

  /** the search request with control */
  private def newSearchRequest(
      listeners:AsyncSearchResultListener with IntermediateResponseListener, 
      cookie:Option[ASN1OctetString]
  ) : SearchRequest = {
    val control = Array[Control]( newSyncReqControl(cookie) )
    
    val request = new SearchRequest(
      listeners, 
      control,
      config.baseDn,
      SearchScope.valueOf( config.searchScope ),
      DereferencePolicy.NEVER,
      config.searchSizeLimit,
      config.searchTimeout,
      /* typesOnly  */ false,
      config.filter,
      config.attributes :_*
    )
    request.setIntermediateResponseListener(listeners)
    LOG.debug("Search request: {}" , request)
    request
  }


  private def saveCookie(cookie:ASN1OctetString) : Unit = {
    if(null != cookie && null != cookie.getValue) {
      cookiePersister.storeCookie(config.replicaId, cookie.getValue)
    }
  }
  
  /**
   * The SyncActor class, which defined all the client
   * behaviour in the synchronization.
   */
  private class SyncActor extends Actor with AsyncSearchResultListener with IntermediateResponseListener {
    syncActor => 
    
    private val logger = LoggerFactory.getLogger(classOf[SyncActor])

    private var asyncRequestId : AsyncRequestID = null
    
    //we give the connection handler its own thread
    private var syncState : SyncConnectionState = SyncStopped
    
    private def startSync() : Unit = {
      /*
       * if the sync is started and we are in one shot, 
       * relaunch
       */
      if(syncState == SyncStopped || !config.isRefreshPersist) {
        val cookie = cookiePersister.readCookie(config.replicaId).map(x => new ASN1OctetString( x ) )
      
        connectionProvider.connection.foreach { connection => 
          asyncRequestId = connection.asyncSearch( newSearchRequest(this,cookie) )
        }
        syncState  = SyncStarted
      } else {
        logger.info("Connection already started in persist mode")
      }
    }
    
    private def stopSync() : Unit = {
      /*
       * Stop the connection if still up, clean state
       */
      if(syncState == SyncStarted) {
        connectionProvider.connection.foreach { connection => 
          connection.abandon(this.asyncRequestId)
        }
        connectionProvider.disconnect
        syncState = SyncStopped
      } else {
        logger.info("Connection already stopped")
      }
    }
    
    def getSyncStatus:SyncConnectionState = synchronized { syncState }
    
    /**
     *  Actor message processing
     */
    override def act = { while(true) { receive {
      case StartSync => 
        logger.debug("Receive a StartSync message, try to establish connection")
        startSync()
      
      case StopSync => 
        logger.debug("StopSync message received")
        stopSync
        
      case x => 
        LOG.warn("Ignoring unknown response type: {}", x)
    } } }

    //Search Result listener method implementation
    override def searchReferenceReturned(searchReference:SearchResultReference){
      /*
       * This is not valid in an Async request. 
       * 
       */
      logger.error("Invalid LDAP response: search reference returned in async request: {}" , searchReference)
    }
    
    //Search Result listener method implementation
    override def searchEntryReturned(searchEntry:SearchResultEntry) {
      logger.debug( "------------- starting handleSearchResult ------------" )
      try {
      
        /*
         * Handle only search result with the ContentSyncStateControl
         */
        searchEntry.getControl(ContentSyncStateControl.SYNC_STATE_OID) match {
          case null => logger.error("Don't know how to handle search entry without ContentSyncStateControl: {}", searchEntry)
          case contentSyncStateControl:ContentSyncStateControl => 
            val (dn, uuid, attributes) = 
              (
                searchEntry.getDN, 
                contentSyncStateControl.getEntryUUID,
                searchEntry.getAttributes.map(a => (a.getName, a.getValues.toList)).toMap
              )
              
            contentSyncStateControl.getState match {
              case ContentSyncState.ADD     => syncMessageListener.foreach { _.sync(Add(dn,uuid.toString,attributes)) }
              case ContentSyncState.MODIFY  => syncMessageListener.foreach { _.sync(Modify(dn,uuid.toString,attributes)) }
              case ContentSyncState.DELETE  => syncMessageListener.foreach { _.sync(Delete(dn,uuid.toString)) }
              case ContentSyncState.PRESENT => syncMessageListener.foreach { _.sync(Present(dn,uuid.toString)) }
            }
            
            //save updated cookie
            saveCookie(contentSyncStateControl.getCookie)
            
            logger.debug( "------------- end handleSearchResult ------------" );
          
          case x => logger.error("Don't know how to handle search entry without ContentSyncStateControl: {}", searchEntry)
        }
      } catch {
        case e : Exception => logger.error("Got an exception when processing SearchResultEntry" , e)
      }
    }
    
    //Async Search Result listener method implementation
    override def searchResultReceived(requestID:AsyncRequestID , searchResult:SearchResult) {
      logger.debug("searchResultReceived: asyncId: {} \nresult: {}" , requestID, searchResult)
      try {
        
        /*
         * We only handle SearchResult with 
         * ContentSyncDoneControl 
         */
        searchResult.getResultCode match {
          case ResultCode.E_SYNC_REFRESH_REQUIRED =>
            /*
                The server may return e-syncRefreshRequired
                result code on the initial content poll if it is safe to do so when
                it is unable to perform the operation due to various reasons.
                reloadHint is set to FALSE in the SearchRequest Message requesting
                the initial content poll.
                
                TODO: Q: The default value is already FALSE then why should this be set to FALSE
                and how that will help in achieving convergence? should the cookie be reset to null?)
             */
            logger.debug("Got a E_SYNC_REFRESH_REQUIRED")
            cookiePersister.removeCookie(config.replicaId)
            //and if we are not in persist mode, relaunch
            if(!config.isRefreshPersist) {
              this ! StartSync
            }
          case ResultCode.SUCCESS => 
            searchResult.getResponseControl(ContentSyncDoneControl.SYNC_DONE_OID) match {
              case null => logger.error("Unknow result message receive: {}", searchResult)
              case contentSyncDoneControl: ContentSyncDoneControl => 
                //handle content sync done control
                logger.debug( "///////////////// inside handle SearchDone //////////////////" )
                
                //save cookie last value
                saveCookie(contentSyncDoneControl.getCookie)
  
                //if refresh delete is present, and we are in one shot mode, relaunch
                logger.debug( "refreshDeletes: " + contentSyncDoneControl.refreshDeletes)
  
                this ! StopSync
                logger.debug( "///////////////// end handle SearchDone //////////////////" )
  
              case x => logger.error("Unknow control in result message receive: {}", searchResult)
            }
          
          
          case x => 
            logger.error("Sync operation was not successful, Error result code received: {}", x)
            this ! stopSync
        }
        
      } catch {
        case e:Exception => logger.error("Got an exception when processing SearchResult", e)
      }
    }
    
    
    override def intermediateResponseReturned(intermediateResponse:IntermediateResponse ) {
      logger.debug( "............... inside handleSyncInfo ..............." )
      try {
        ContentSyncInfoIntermediateResponse.decode(intermediateResponse) match {
          case syncInfoValue:ContentSyncInfoIntermediateResponse =>
            
            val uuidList = syncInfoValue.getEntryUUIDs match {
              case null => Nil
              case l => l.toList.map( _.toString )
            }
      
            if(logger.isDebugEnabled) {
              logger.debug( "refreshDeletes: " + syncInfoValue.refreshDeletes )
              logger.debug( "refreshDone: " + syncInfoValue.refreshDone )
              val opType = if(syncInfoValue.refreshDeletes) "DEL" else "PRES"
              for(uuid <- uuidList) {
                logger.debug( "{} uuid: {}", opType, uuid )
              }
            }
  
            // if refreshDeletes set to true then delete all the entries with entryUUID
            // present in the syncIdSet 
            if(!uuidList.isEmpty) {
              if(syncInfoValue.refreshDeletes) {
                syncMessageListener.foreach { _.sync(MassDelete(uuidList)) }
              } else {
                syncMessageListener.foreach { _.sync(MassPresent(uuidList)) }
              }
            }
            saveCookie(syncInfoValue.getCookie )
                    
            logger.debug( "............... end handleSyncInfo ..............." )
            
          case x => logger.error("Unknown intermediate response: {}", x)
        }
      } catch {
        case e:Exception => logger.error("Got an exception when processing intermediate state response", e)
      }
    }
  }
}