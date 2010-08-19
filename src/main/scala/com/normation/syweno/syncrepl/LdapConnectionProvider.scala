package com.normation.syweno.syncrepl


import org.slf4j.LoggerFactory
import net.liftweb.common._
import net.liftweb.util.ControlHelpers.tryo


/**
 * Interface in charge to connect and bind to the provider.
 * 
 * It's just a facade to a framework provided "Ldap Connection", 
 * on which we just provided sugar to use our config files.
 * It is also able to keep and reuse an internal connection
 * for several operations. 
 */
trait LdapConnectionProvider[C] {
  def config:ProviderConfiguration
  
  /**
   * Return the connection to provider if bind succeeded.
   * A bind to provider is tried only if no valid 
   * connection already exists
   */
  def connection : Box[C]
  
  def disconnect : Unit 
  
}