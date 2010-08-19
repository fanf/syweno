package bootstrap.liftweb

import net.liftweb.common._
import net.liftweb.util._
import net.liftweb.http._
import provider.HTTPRequest
import net.liftweb.sitemap._
import net.liftweb.sitemap.Loc._
import Helpers._

import com.normation.syweno.comet._

/**
  * A class that's instantiated early and run.  It allows the application
  * to modify lift's environment
  */
class Boot {
  def boot {
    // where to search snippet
    LiftRules.addToPackages("com.normation.syweno")

    
    LiftRules.ajaxStart = Full(() => LiftRules.jsArtifacts.show("ajax-loader").cmd)
    LiftRules.ajaxEnd   = Full(() => LiftRules.jsArtifacts.hide("ajax-loader").cmd)  
    LiftRules.early.append(makeUtf8)

    LiftRules.cometCreation.append {
      case CometCreationInfo("ApachedsLdapLog",name,defaultXml,attributes,session) => {
        var jsVarName = S.attr("jsVarName") match {
          case Full(id) => id
          case _ => "oTable"
        }
        val widget = new LdapLog(session, Full("ApachedsLdapLog"), name, defaultXml, attributes,jsVarName)
        ApacheDSSyncreplConsumer.registerSyncMessageListener(widget)
        widget
      }
      case CometCreationInfo("UnboundidLdapLog",name,defaultXml,attributes,session) => {
        var jsVarName = S.attr("jsVarName") match {
          case Full(id) => id
          case _ => "oTable"
        }
        val widget = new LdapLog(session, Full("UnboundidLdapLog"), name, defaultXml, attributes,jsVarName)
        UnboundIDSyncreplConsumer.registerSyncMessageListener(widget)
        widget
      }
    }   
    
    // Build SiteMap
    val entries = Menu(Loc("Home", List("index"), "Home")) :: Nil
    LiftRules.setSiteMap(SiteMap(entries:_*))
    
  }

  private def makeUtf8(req: HTTPRequest): Unit = {req.setCharacterEncoding("UTF-8")}  
}



