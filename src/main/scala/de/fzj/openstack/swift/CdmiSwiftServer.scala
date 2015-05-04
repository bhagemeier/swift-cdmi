package de.fzj.openstack.swift

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

import scala.collection.JavaConversions.iterableAsScalaIterable
import scala.collection.immutable

import org.apache.commons.codec.binary.Base64
import org.bouncycastle.cms.CMSSignedData
import org.codehaus.jackson.`type`.TypeReference
import org.codehaus.jackson.map.DeserializationConfig
import org.codehaus.jackson.map.ObjectMapper
import org.javaswift.joss.client.factory.AccountConfig
import org.javaswift.joss.client.factory.AccountFactory
import org.javaswift.joss.client.factory.AuthenticationMethod
import org.javaswift.joss.client.factory.AuthenticationMethod.AccessProvider
import org.javaswift.joss.command.shared.identity.access.AccessTenant
import org.javaswift.joss.model.Access
import org.javaswift.joss.model.Account

import com.twitter.app.App
import com.twitter.app.GlobalFlag
import com.twitter.finagle.http.Status
import com.twitter.logging.Level
import com.twitter.logging.Logging
import com.twitter.util.Future

import gr.grnet.cdmi.model.ContainerModel
import gr.grnet.cdmi.model.ObjectModel
import gr.grnet.cdmi.service.CdmiRestService
import gr.grnet.cdmi.service.CdmiRestServiceHandlers
import gr.grnet.cdmi.service.CdmiRestServiceMethods
import gr.grnet.cdmi.service.CdmiRestServiceResponse
import gr.grnet.cdmi.service.CdmiRestServiceTypes
import gr.grnet.common.http.StdMediaType
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

  System.setProperty("javax.net.ssl.trustStore", "src/main/resources/all_igtf.jks")
  System.setProperty("javax.net.ssl.trustStorePass", "grid-security")

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

  //override def flags: Seq[GlobalFlag[_]] = super.flags ++ Seq(tokensURL)

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

  override def GET_container_cdmi(request: Request, containerPath: List[String]): Future[Response] = {
    val account = createJossAccount(request.headers.get("x-auth-token"))
    val (container, path) = extractContainerAndPath(containerPath, true)
    val containerElements = account.getContainer(container).listDirectory(path, '/', null, 1000)
    val elementNames = (for (element <- containerElements) yield element.getName).asInstanceOf[immutable.Seq[String]]
    val cModel = ContainerModel(
      objectID = containerPath.mkString("/")+"/",
      objectName = containerPath.mkString("/")+"/",
      parentURI = containerPath.take(containerPath.length - 1).mkString("/") + "/",
      parentID = containerPath.take(containerPath.length - 1).mkString("/") + "/",
      domainURI = "",
      childrenrange = if (elementNames.length == 0) "" else "0-" + (elementNames.length - 1).toString(),
      children = elementNames)
    okAppCdmiContainer(request, Json.objectToJsonString(cModel))
  }

  private def DELETE_object_(request: Request, objectPath: List[String]) : Future[Response] = {
    val account = createJossAccount(request.headers().get("x-auth-token"))
    val (container, path) = extractContainerAndPath(objectPath, false)
    account.getContainer(container).getObject(path).delete()

    // TODO return HTTP "204 No Content" rather than "200 OK"
    // TODO proper error handling
    okTextPlain(request)
  }

  override def DELETE_object_cdmi(request: Request, objectPath: List[String]) : Future[Response] = {
    DELETE_object_(request, objectPath)
  }

  override def DELETE_object_noncdmi(request: Request, objectPath: List[String]) : Future[Response] = {
    DELETE_object_(request, objectPath)
  }

  override def GET_object_cdmi(request: Request, objectPath: List[String]): Future[Response] = {
    val account = createJossAccount(request.headers.get("x-auth-token"))

    val (container, path) = extractContainerAndPath(objectPath, false)

    // TODO need to deal with "sub-directories" here, as the path gets encoded
    //      and each "/" becomes a "%2F"
    // Asked about this behaviour at https://github.com/javaswift/joss/issues/77
    // may need a separate branch of JOSS to get going
    val swObjectHdl = account.getContainer(container).getObject(path)
    val swObject = swObjectHdl.downloadObject()
    val valuetransferencoding = if(swObjectHdl.getContentType.equals("text/plain")) { "utf-8"} else { "base64" }
    val encodedObject = if (valuetransferencoding.equals("base64")) {
      new String(Base64.encodeBase64(swObject))
    } else {
      new String(swObject)
    }

    val model = ObjectModel(
        objectID = objectPath.tail.mkString("/"),
        objectName = objectPath.tail.mkString("/"),
        parentURI = objectPath.take(objectPath.length-1).mkString("/"),
        parentID = objectPath.take(objectPath.length-1).mkString("/"),
        domainURI = "",
        mimetype = swObjectHdl.getContentType,
        valuetransferencoding = valuetransferencoding,
        metadata = Map[String, String](),
        valuerange = "0-" + (swObject.length-1).toString(),
        value = encodedObject
    )

    val json = Json.objectToJsonString(model)

    okAppCdmiObject(request, json)
  }

  override def GET_object_noncdmi(request: Request, objectPath: List[String]): Future[Response] = {
    val account = createJossAccount(request.headers.get("x-auth-token"))

    val (container, path) = extractContainerAndPath(objectPath, false)

    val swObjectHdl = account.getContainer(container).getObject(path)
    val swObject = swObjectHdl.downloadObject
    val content_type = swObjectHdl.getContentType

    val response = Response(request)
    response.headers().set("content-type", content_type)
    response.write(swObject)

    Future(response)
  }

  private def extractContainerAndPath(objectPath: List[String], appendTrailingSlash: Boolean) : (String, String) = {
    val container = objectPath head
    // TODO the following expression smells, there must be a better, shorter way
    val path = {if (appendTrailingSlash) {(objectPath.tail mkString "/") + "/" }
      else { objectPath.tail mkString "/"}}
      match { case "/" => ""; case s => s}
    (container, path)
  }

  private def createJossAccount(x_auth_token: String): Account = {
    object tokenAccessProvider extends AccessProvider {
      val b64token = x_auth_token.replace('-', '/')
      val decodedToken = Base64.decodeBase64(b64token);
      val cms = new CMSSignedData(decodedToken);
      val cmsTokenPayload = cms.getSignedContent();
      val tokenPayload = new String(cmsTokenPayload.getContent().asInstanceOf[Array[Byte]])

      val om = new ObjectMapper()
      om.configure(DeserializationConfig.Feature.UNWRAP_ROOT_VALUE, true)
      val access = om.readValue[AccessTenant](tokenPayload, typeReference[AccessTenant])
      access.token.id = x_auth_token

      private [this] def typeReference[T: Manifest] = new TypeReference[T] {
        override def getType = typeFromManifest(manifest[T])
      }

      private [this] def typeFromManifest(m: Manifest[_]): Type = {
        if (m.typeArguments.isEmpty) { m.runtimeClass }
        else new ParameterizedType {
          def getRawType = m.runtimeClass
          def getActualTypeArguments = m.typeArguments.map(typeFromManifest).toArray
          def getOwnerType = null
        }
      }

      override def authenticate() : Access = {
        access
      }
    }

    val config = new AccountConfig()
    config.setAuthenticationMethod(AuthenticationMethod.EXTERNAL)
    config.setAuthUrl(getKeystoneTokenEndpoint(tokenAccessProvider.access))
    config.setAccessProvider(tokenAccessProvider)
    //config.setTenantName(tokenAccessProvider.access.token.tenant.name)
    config.setTenantId(tokenAccessProvider.access.token.tenant.id)
    config.setUsername(tokenAccessProvider.access.user.name)
    config.setAllowReauthenticate(false)
    config.setAllowCaching(true)
    config.setDelimiter('/')
    val af = new AccountFactory(config)
    af.createAccount()
  }

  private def getKeystoneTokenEndpoint(access : AccessTenant) : String = {
    for (serviceCatalog <- access.serviceCatalog) {
      if("keystone".equals(serviceCatalog.name)) {
        return serviceCatalog.getRegion(access.getPreferredRegion).publicURL + "/tokens";
      }
    }
    return ""
  }


  override def PUT_object_cdmi_create(request: Request, objectPath: List[String]): Future[Response] = {
    notImplemented(request)
  }


  /**
   * Leave this unimplemented for now (initial development)
   * 
   * TODO: must be implemented, see super class method
   */
  override def DELETE_object_or_queue_or_queuevalue_cdmi(request: Request, reqPath: List[String]) = {
    DELETE_object_cdmi(request, reqPath)
  }

  override def handleRootCall(request: Request) : Future[Response] = {
    val account = createJossAccount(request.headers().get("x-auth-token"))
    val swContainers = account.list()
    val swContainerNames = (for(swContainer <- swContainers) yield swContainer.getName + "/").asInstanceOf[immutable.Seq[String]]

    val container = ContainerModel(
      objectID = "/",
      objectName = "/",
      parentURI = "/",
      parentID = "/",
      domainURI = "",
      childrenrange = if (swContainers.size() == 0) "" else "0-" + (swContainers.size()-1).toString(),
      children = swContainerNames
      )

      okAppCdmiContainer(request, Json.objectToJsonString(container))
  }

  /**
   * This method is probably not needed as a call to root is usually sent as
   *   GET / HTTP/1.1
   *   such that the slash will be there
   */
  override def handleRootNoSlashCall(request: Request) : Future[Response] = {
    val resp = response(request, Status.MovedPermanently, StdMediaType.Text_Plain, "")
    val reqLoc = request.getUri()
    resp.headers().set("Location", reqLoc + "/")
    Future(resp)
  }

}
