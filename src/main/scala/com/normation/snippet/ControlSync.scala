package com.normation.snippet

import bootstrap.liftweb.{UnboundIDSyncreplConsumer,ApacheDSSyncreplConsumer}
import com.normation.syncrepl._
import apacheds._
import unboundid._
import scala.xml._
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.util.Helpers._
import net.liftweb.http.js._
import JsCmds._
import JE._

/**
 * Simple snippet that display a "start" or "stop" button.
 * When the button is clicked, an ajax call is performed to:
 * - execute the corresponding command;
 * - redraw the button according to the new state of the synchronization.
 */
class ControlSync {
  
  private val freshId = nextFuncName + "_sync"
  
  //render button when the browser perform a GET on the page
  def render : NodeSeq = {
    S.attr("provider") match {
      case Full("apacheds") => 
        val id = "apacheds" + freshId 
        <span id={id}>Apache DS: {button(ApacheDSSyncreplConsumer,id)}</span>
      case Full("unboundid") => 
        val id = "unboundid" + freshId 
        <span id={id}>UnboundId: {button(UnboundIDSyncreplConsumer,id)}</span>
      case _ => <span>Error: missing parameter "provider"</span>
    }
    
  }
  
  def head() : NodeSeq = {
    Script(JsRaw("""|
        | var sendMessage = function(node, callback) { node.disabled = "disabled" ; callback() ; };
        |""".stripMargin('|')))
  }
  
  //choose what button to display, and link the action to exec when clicked
  private def button(consumer:SyncReplConsumer,id:String) = consumer.getSyncStatus match {
    case SyncStarted => 
      SHtml.ajaxButton("Stop sync", Call("sendMessage", JsRaw("this")) , () => redrawButton(StopSync,consumer,id))
    case SyncStopped =>
      SHtml.ajaxButton("Start sync", Call("sendMessage", JsRaw("this")), () => redrawButton(StartSync,consumer,id))
  }
  
  //action to exec when a button is clicked
  private def redrawButton(
      message:SyncConnectionControlMessage,
      consumer:SyncReplConsumer,
      id:String
  ) : JsCmd = {
    consumer.syncSearchManager ! message
    Thread.sleep(2000)
    SetHtml(id, button(consumer,id))
  }

}