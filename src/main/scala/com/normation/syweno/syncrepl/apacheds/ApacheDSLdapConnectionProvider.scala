package com.normation.syweno.syncrepl
package apacheds

import org.slf4j.LoggerFactory
import net.liftweb.common._
import net.liftweb.util.ControlHelpers.tryo

import org.apache.directory.ldap.client.api.LdapConnection
import org.apache.directory.ldap.client.api.message.BindResponse
import org.apache.directory.shared.ldap.message.ResultCodeEnum


/**
 * Class in charge to connect and bind to the provider.
 * It's just a facade to a framework provided "Ldap Connection", 
 * on which we just provided sugar to use our config files.
 * It is also able to keep and reuse an internal connection
 * for several operations. 
 */
class ApacheDSLdapConnectionProvider(
  val config:ProviderConfiguration
) extends LdapConnectionProvider[LdapConnection] {
  
  private val LOG = LoggerFactory.getLogger(classOf[ApacheDSLdapConnectionProvider])
  private var internalConnection : Box[LdapConnection] = Empty
  
  /**
   * Return the connection to provider if bind succeeded.
   * A bind to provider is tried only if no valid 
   * connection already exists
   */
  def connection : Box[LdapConnection] = internalConnection match {
    case Full(con) if(!con.isConnected) => 
      disconnect
      connection
    case Full(con) => internalConnection 
    case _ =>
      val connection = new LdapConnection( config.providerHost, config.providerPort)
    
      internalConnection = (for {
        con <- tryo(connection.bind( config.bindDn, config.credentials ))
        boundCon <- (con match {
          case null => Failure("Failed to bind with the given bindDN and credentials")
          case bindResponse:BindResponse => tryo(bindResponse.getLdapResult.getResultCode) match {
            case Full(ResultCodeEnum.SUCCESS) => 
              Full(connection)
            case _ => 
              Failure("Failed to bind on the server : %s".format(bindResponse.getLdapResult))
          }
        })
      } yield {
        boundCon
      })
      
      //log restul
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
          unbound <- tryo(con.unBind)
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