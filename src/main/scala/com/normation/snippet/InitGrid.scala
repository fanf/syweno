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
 * A snippet that take in charge to init DataTables
 */
class InitGrid {

  def inHead() : NodeSeq = {
    var jsVarName = S.attr("jsVarName") match {
      case Full(id) => id
      case _ => "oTable"
    }

    Script(JsRaw("""|
        | var %s;
        | $(document).ready(function() {
        |   %s = $('#%s').dataTable({
        |     "bJQueryUI": true,
        |     "sPaginationType": "full_numbers",
        |     "bFilter": true,
        |     "bAutoWidth": true,
        |     "aoColumns": [ 
        |       { sWidth: '200px' , "asSorting": [ "desc" , "asc" ] },
        |       { sWidth: '100px' },
        |       { sWidth: '100px' },
        |       { sWidth: '538px' }
        |     ]
        |   });
        | } );
        |""".stripMargin('|').format(jsVarName,jsVarName,jsVarName)))
  }
  
  def table() : NodeSeq = {
    var jsVarName = S.attr("jsVarName") match {
      case Full(id) => id
      case _ => "oTable"
    }
    
    <table id={jsVarName}>
      <thead>
        <tr>
          <th>Date</th>
          <th>Event type</th>
          <th>Id type</th>
          <th>Entry id</th>
        </tr>
      </thead>
      <tbody>
        <lift:ignore><!-- content will be filled by comet responses --></lift:ignore> 
      </tbody>    
    </table>
  }
}