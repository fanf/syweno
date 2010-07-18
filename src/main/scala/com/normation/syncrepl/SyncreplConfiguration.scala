package com.normation.syncrepl

/*
 * Configuration object used to define the 
 * SyncRepl provider (LDAP master server) and
 * synchronization parameters (persistant or
 * one time, etc) 
 */

/**
 * LDAP master server configuration: define
 * host/port of master server and user used
 * for the synchronization connection.
 */
case class ProviderConfiguration(
  /** host name of the syncrepl provider server */
  providerHost:String,

  /** port number of the syncrepl provider server */
  providerPort: Int,

  /** bind dn */
  bindDn:String,

  /** password for binding with bind dn */
  credentials:String
)

/**
 * Synchronization parameters: define replication
 * ID, search, synchronization type (persistant or
 * one shot), etc. 
 */
case class SyncreplConfiguration(
  providerConfig : ProviderConfiguration,
  
  /** replication id of that consumer */
  replicaId:Int,
  
  /** the base DN whose content will be searched for syncing */
  baseDn:String,

  /** the ldap filter for fetching the entries */
  filter:String,

  /** seq of attribute names */
  attributes:Seq[String],

  /** 
   * the search scope 
   * 0: BASE, 1: ONE LEVEL, 2: SUB TREE
   */
  searchScope:Int = 1,

  /** flag to represent refresh and persist or refresh only mode */
  isRefreshPersist:Boolean = true,

  /** the number for setting the limit on number of search results to be fetched
   * default value is 0 (i.e no limit) */
  searchSizeLimit:Int = 0,

  /** the timeout value to be used while doing a search 
   * default value is 0 (i.e no limit)*/
  searchTimeout:Int = 0,
  
  /**
   * reload hint
   * From man slapo-syncproc:
   * for OpenLDAP > 1.3.11n this option should be set TRUE */
  reloadHint:Boolean = true
)