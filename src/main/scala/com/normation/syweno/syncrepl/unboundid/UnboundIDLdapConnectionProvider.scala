package com.normation.syweno.syncrepl
package unboundid


import org.slf4j.LoggerFactory
import net.liftweb.common._
import net.liftweb.util.ControlHelpers.tryo

import com.unboundid.ldap.sdk.{LDAPConnection,BindResult,ResultCode}


/**
 * Class in charge to connect and bind to the provider.
 * It's just a facade to a framework provided "Ldap Connection", 
 * on which we just provided sugar to use our config files.
 * It is also able to keep and reuse an internal connection
 * for several operations. 
 */
class UnboundIDLdapConnectionProvider(
  val config:ProviderConfiguration
) extends LdapConnectionProvider[LDAPConnection] {
  private val LOG = LoggerFactory.getLogger(classOf[UnboundIDLdapConnectionProvider])
  private var internalConnection : Box[LDAPConnection] = Empty
  
  /**
   * Return the connection to provider if bind succeeded.
   * A bind to provider is tried only if no valid 
   * connection already exists
   */
  def connection : Box[LDAPConnection] = internalConnection match {
    case Full(con) if(!con.isConnected) => 
      disconnect
      connection
    case Full(con) => internalConnection 
    case _ =>
      val connection = new LDAPConnection( config.providerHost, config.providerPort )
    
      internalConnection = (for {
        con <- tryo(connection.bind( config.bindDn, config.credentials ))
        boundCon <- (con match {
          case null => Failure("Failed to bind with the given bindDN and credentials")
          case bindResponse:BindResult => tryo(bindResponse.getResultCode) match {
            case Full(ResultCode.SUCCESS) => 
              Full(connection)
            case _ => 
              Failure("Failed to bind on the server : %s".format(bindResponse.getResultCode.toString))
          }
        })
      } yield {
        boundCon
      })
      
      //log result
      internalConnection match {
        case Failure(m,_,_) => LOG.error(m)
        case Empty => LOG.error("Can not start a connection but no error messages provided")
        case Full(c) => LOG.info("Connected to provider server {}:{}",config.providerHost,config.providerPort)
      }
      
      internalConnection
  }
  
  def disconnect : Unit = {
    internalConnection match {
      case Full(con) => 
        (for {
          unbound <- tryo(con.close)
          closed <- tryo(con.close)
        } yield {
          internalConnection = None
          "ok"
        }) match {
          case Failure(m,_,_) => LOG.error(m)
          case _ => LOG.info("Connection to {} closed",config.providerHost)
        }
      case _ => //already disconnected
    }
  }
}