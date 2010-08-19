package com.normation.syweno.syncrepl

import org.junit.Assert._
import org.junit.{Test,AfterClass}
import java.io.File
import org.apache.commons.io.FileUtils

class TestCookiePersister {

  private def newid = (scala.math.random * 1000).toInt
  
  @Test def createNonExistingDirectory {
    val dirName = "%s/syncreplCookies/%s".format(
      System.getProperty("java.io.tmpdir"),newid.toString
    )
    val dir = new File(dirName)
    
    assertFalse(dir.exists)
    
    val p = new DefaultCookiePersister(dirName)
    
    p.storeCookie(newid, newid.toString.getBytes)
    
    assertTrue(dir.exists)
  }
  
  @Test def cannotRetrieveNonStored {
    val dirName = "%s/syncreplCookies/%s".format(
      System.getProperty("java.io.tmpdir"),newid.toString
    )
    val p = new DefaultCookiePersister(dirName)
    
    val id = newid
    assertEquals(None, p.readCookie(id))
    val b = newid.toString.getBytes
    p.storeCookie(id, b)
    
    assertTrue(p.readCookie(id).isDefined)
    assertArrayEquals(b,p.readCookie(id).get)
    
    p.removeCookie(id)
    assertEquals(None, p.readCookie(id))
    
  }
  
  @Test def storeRetrieveDelete {
    val dirName = "%s/syncreplCookies/%s".format(
      System.getProperty("java.io.tmpdir"),newid.toString
    )
    val p = new DefaultCookiePersister(dirName)
    val id = newid
    
    assertEquals(None, p.readCookie(id))
  }
}

object TestCookiePersister {
  @AfterClass def cleanDir {
    FileUtils.deleteDirectory(
      new File("%s/syncreplCookies".
       format(System.getProperty("java.io.tmpdir"))
      )
    )
  }
}