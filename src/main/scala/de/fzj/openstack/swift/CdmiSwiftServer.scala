package de.fzj.openstack.swift

import gr.grnet.cdmi.service.CdmiRestService
import gr.grnet.cdmi.service.CdmiRestServiceTypes
import com.twitter.logging.Logging
import gr.grnet.cdmi.service.CdmiRestServiceHandlers
import gr.grnet.cdmi.service.CdmiRestServiceResponse
import com.twitter.logging.Level
import gr.grnet.cdmi.service.CdmiRestServiceMethods
import com.twitter.app.App
import com.twitter.app.GlobalFlag
import com.twitter.finagle.http.Request
import com.twitter.util.Future
import gr.grnet.common.http.StdMediaType
import gr.grnet.cdmi.capability.SystemWideCapability
import com.twitter.finagle.http.Status
import gr.grnet.cdmi.http.CdmiMediaType
import gr.grnet.common.json.Json

/**
 * @author bjoernh
 */
object CdmiSwiftServer extends CdmiRestService
  with App with Logging
  with CdmiRestServiceTypes
  with CdmiRestServiceHandlers
  with CdmiRestServiceMethods
  with CdmiRestServiceResponse {
  
  object swiftURL extends GlobalFlag[String](
      "https://swift.zam.kfa-juelich.de:8888/v1",
      "Swift service URL")
      
  /**
   * @deprecated I would like to avoid duplication of information,
   *             so let's consider this property only an idea
   * 
   */
  @deprecated
  object authURL extends GlobalFlag[String](
      "https://fsd-cloud.zam.kfa-juelich.de:5000/v2.0",
      "Keystone URL")
      
  object tokensURL extends GlobalFlag[String](
      "https://fsd-cloud.zam.kfa-juelich.de:5000/v2.0/tokens",
      "Used to obtain UUID from token")
    
  override def defaultLogLevel: Level = Level.DEBUG
  
  /**
   * This may need to be overridden to allow for backend specific
   * capabilities. Right now, we'll use the default set.
   * 
   * TODO this was just a development test, so commented for now
   */
  /*override def GET_capabilities(request : Request) : Future[Response] = {
    val caps = systemWideCapabilities
    val myCaps = caps.capabilities + (SystemWideCapability.cdmi_logging → true.toString)
    val jsonCaps = Json.objectToJsonString(myCaps)
    
    Future(response(request, Status.Ok, CdmiMediaType.Application_CdmiCapability, jsonCaps))
  }*/
  
  override def GET_container_cdmi(request: Request, containerPath: List[String]) : Future[Response]= {
    // 1. if x-auth-token is set, try and retrieve the account URL, as it should
    //    be handled transparently for CDMI
    // 2. if x-auth-token is not set, forward the request and get Keystone URL
    //    from Swift (we could also use our own authURL, but would not like
    //    to duplicate information)
    //    a) return 401 with www-authenticate: Keystone ... header
    //         next attempt should contain x-auth-token
    
    notImplemented(request, "bulla", StdMediaType.Text_Plain)
  }
  
  /**
   * Leave this unimplemented for now (initial development)
   * 
   * TODO: must be implemented, see super class method
   */
  override def DELETE_object_or_queue_or_queuevalue_cdmi(request: Request, path: List[String]) =
    notImplemented(request)
  
}