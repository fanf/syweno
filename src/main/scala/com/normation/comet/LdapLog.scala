package com.normation
package comet

import syncrepl._

import net.liftweb._
import http._
import common._
import util._
import Helpers._
import js._
import JsCmds._
import JE._
import scala.xml.{Text, NodeSeq}

import org.joda.time.{DateTime,Period}
import org.slf4j.LoggerFactory

/*
 * Each time we receive an entry update, we display it
 */
class LdapLog(initSession: LiftSession,
              initType: Box[String],
              initName: Box[String],
              initDefaultXml: NodeSeq,
              initAttributes: Map[String, String],
              datatable : String
) extends CometActor with SyncMessageListener {
  
  val logger = LoggerFactory.getLogger(classOf[LdapLog])
  
  override def defaultPrefix = Full("ldaplog")  
  
  initCometActor(initSession, initType, initName, initDefaultXml, initAttributes)
  
  private def now = new DateTime
  
  private var lastAddRow = now
  private var discared = 0
  private var processed = 0
  private val limit = 5
  
  def render = NodeSeq.Empty 
  
  /*
   * That's just a demo example, we don't want to send thousand of
   * rows to the browser.
   * So we allows at most one update by 100 millis second, and take
   * count of how many were discards. 
   */
  def addRow(time:DateTime, operation:String, idType:String, id:String) : JsCmd = {
    //add an highlight to the row
    def addHighlight(time:String, operation:String, idType:String, id:String) : JsCmd = {
      JsRaw("var x = " + datatable + """.fnAddData([%s,%s,%s,%s])""".format(
        time, 
        operation,
        idType,
        id.replaceAll(",",", "))) &
      JsRaw("""|
          | var nTr = %s.fnSettings().aoData[ x[0] ].nTr;
          | $(nTr).effect("highlight", {}, 3000);
          |""".stripMargin('|').format(datatable))
    }
    
    val n = now
    val elapsed = (new Period(lastAddRow,n)).getMillis
    
    elapsed > 500 match {
      case true => //ok, reset all
        lastAddRow = n
        processed = 0
        
        (if(discared > 0) {
          val i = discared
          discared = 0
          addHighlight(
            time.toString().encJs,
            "_".encJs,
            "_".encJs,
            (i.toString + "entry(ies) were discared (too many updates in 500ms)").encJs
          )
        } else {
          Noop
        }) & addHighlight(
              time.toString().encJs, //encJs add "" around the string
              operation.encJs,
              idType.encJs,
              id.encJs)
      
      case false if(processed >= limit) => //Discard it
        discared = discared + 1
        Noop
      
      case false if(processed < limit) => //process, but update processed
        lastAddRow = n
        processed = processed + 1
        
        addHighlight(
          time.toString().encJs, //encJs add "" around the string
          operation.encJs,
          idType.encJs,
          id.encJs)
    }    
  }
  
  override def sync(message:SyncEntryMessage) : Unit = {
    message match {
      case Add(dn,uuid, attrs) =>
        partialUpdate(addRow(now,"ADD", "DN", dn))
      case Modify(dn,uuid, attrs) => 
        partialUpdate(addRow(now,"MOD", "DN", dn))
      case Delete(dn,uuid) =>
        partialUpdate(addRow(now,"DEL", "DN", dn))
      case Present(dn,uuid) => 
        partialUpdate(addRow(now,"PRESENCE", "DN", dn))
      case MassDelete(uuids) =>
        val n = now
        partialUpdate(
          uuids.foldLeft(Noop)( (js,uuid) => 
            js & addRow(n,"DEL", "UUID", uuid)
          )
        )
      case MassPresent(uuids) =>
        val n = now
        partialUpdate(
          uuids.foldLeft(Noop)( (js,uuid) => 
            js & addRow(n,"PRESENCE", "UUID", uuid)
          )
        )
    }
  }
}
