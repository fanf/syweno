package com.normation.syncrepl

import org.slf4j.{LoggerFactory,Logger}
import java.io.{File,FileOutputStream,FileInputStream}

/**
 * SyncRepl protocol uses a client side cookie to
 * store the last client synchronization point. 
 * 
 * A cookie is a byte array black box, only understood
 * by the the server which send it. 
 * 
 * This trait is the interface for the client side cookie 
 * persister service.
 * 
 * We only need to be able to save/retrieve/delete a 
 * cookie for a given replication, identified by
 * its id. 
 *
 */
trait CookiePersister {
  /**
   * stores the cookie in a persistence database
   */
  def storeCookie(replicaId:Int, syncCookie:Array[Byte]) : Unit
  
  /**
   * deletes the cookie if it exists
   */
  def removeCookie(replicaId:Int) : Unit 
  
  /**
   * retrieve the cookie if exists. 
   */
  def readCookie(replicaId:Int) : Option[Array[Byte]] 
}

/**
 * 
 * A default implementation of the cookie persister which
 * save and retrieve cookies on the file system
 * (by default on the System_temp_dir/syncreplCookies 
 * directory).
 * The cookie will be named with the replicaId which 
 * created it. 
 */
class DefaultCookiePersister(
  val cookieDir : String = 
    "%s/syncreplCookies".format(System.getProperty("java.io.tmpdir"))
) extends CookiePersister {
  
  private val LOG = LoggerFactory.getLogger(classOf[DefaultCookiePersister])

  private def mkCookieDir : Unit = {
    val dir = new File(cookieDir)
    (dir.exists, dir.isDirectory, dir.canWrite) match {
      case (false, _, _) => 
        if(!dir.mkdirs) { error("Can not create directory %s".format(dir.getAbsolutePath)) }
      case (true, false, _) =>
        error("%s is not a directory".format(dir.getAbsolutePath))
      case (true, true, false) =>
        error("%s is not a writable".format(dir.getAbsolutePath))
      case _ => //ok
    }
  }
  
  /**
   * stores the cookie in a file.
   */
  override def storeCookie(replicaId:Int, syncCookie:Array[Byte]) : Unit = {
    mkCookieDir
    if ( syncCookie != null ) {
      try {
        val fout = new FileOutputStream( new File(cookieDir, replicaId.toString) )
        fout.write( syncCookie.length )
        fout.write( syncCookie )
        fout.close()

        LOG.debug( "Store cookie for replica ID {}", replicaId );
      } catch {
        case e:Exception => 
          LOG.error( "Failed to store cookie", e );
      }
    }
  }

  /**
   * read the cookie from a file(if exists).
   */
  override def readCookie(replicaId:Int) : Option[Array[Byte]] = {
    try {
      val cookieFile = new File(cookieDir, replicaId.toString)
      if ( cookieFile.exists() && ( cookieFile.length() > 0 ) ) {
        val fin = new FileInputStream( cookieFile )
        val syncCookie = new Array[Byte](fin.read())
        fin.read( syncCookie )
        fin.close
        LOG.debug( "Read cookie from file {}", cookieFile.getAbsolutePath );
        Some(syncCookie)
      } else None
    } catch {
      case e:Exception => 
      LOG.error( "Failed to read the cookie {}", e )
      None
    }
  }


  /**
   * deletes the cookie file if it exists and is not empty
   */
  override def removeCookie(replicaId:Int) : Unit = {
    val cookieFile = new File(cookieDir, replicaId.toString)
    if ( cookieFile.exists() && ( cookieFile.length() > 0 ) ) {
      val deleted = cookieFile.delete()
      LOG.info( "Delete cookie file {}", deleted )
    }
  }
}
