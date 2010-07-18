package com.normation.snippet

import scala.xml._
import bootstrap.liftweb.SyncReplConfig

/**
 * Simple snippet that display chosen 
 * configuration informations
 */
class LdapServerInfos {
  
  def render : NodeSeq = {
    <div style="padding-left:5px;padding-right:5px;padding-bottom:15px;">
      <h2>LDAP server connection</h2>
      <ul>
        <li><b>Host:</b> {SyncReplConfig.provider.providerHost}</li>
        <li><b>Port:</b> {SyncReplConfig.provider.providerPort}</li>
        <li><b>Bind DN:</b> {SyncReplConfig.provider.bindDn}</li>
      </ul>
      
      <h2>Synchronization parameter</h2>
      <ul>
        <li><b>Replica ID:</b> {SyncReplConfig.sync.replicaId}</li>
        <li><b>Mode:</b> {
          if(SyncReplConfig.sync.isRefreshPersist) "Continuous synchronization" 
          else "One time synchronization"
        }</li>
        <li><b>Base DN:</b> {SyncReplConfig.sync.baseDn}</li>
        <li><b>Filter:</b> {SyncReplConfig.sync.filter}</li>
        <li><b>Search scope:</b> {SyncReplConfig.sync.searchScope match {
          case 0 => "Object"
          case 1 => "One level"
          case 2 => "Subtree"
          case _ => "Bad scope configuration"
        }}</li>
        <li><b>Attributes:</b> {
          if(SyncReplConfig.sync.attributes.isEmpty) " *"
          else SyncReplConfig.sync.attributes.mkString(",")
        }</li>
      </ul>
    </div>
  }
}
