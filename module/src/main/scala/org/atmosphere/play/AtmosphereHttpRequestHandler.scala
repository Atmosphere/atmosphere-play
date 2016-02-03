package org.atmosphere.play

import com.google.inject.Inject
import play.api.http._
import play.api.mvc.{Handler, RequestHeader}
import play.api.routing.Router
import play.core.j._
import play.libs.F.Promise


class AtmosphereHttpRequestHandler @Inject()(components: JavaHandlerComponents,
                                             router: Router,
                                             errorHandler: HttpErrorHandler,
                                             configuration: HttpConfiguration,
                                             filters: HttpFilters)
  extends JavaCompatibleHttpRequestHandler(router, errorHandler, configuration, filters, components) {

  override def routeRequest(request: RequestHeader) = {
    dispatch(request) match {
      case Some(handler) =>
        Option(handler)
      case None =>
        super.routeRequest(request)
    }
  }

  private def isWsSupported(upgradeHeader: Option[String], connectionHeaders: Seq[String]): Boolean =
    upgradeHeader.map("websocket".equalsIgnoreCase)
      .getOrElse(connectionHeaders.contains("upgrade".equalsIgnoreCase _))

  private def dispatch(requestPath: String,
                       controllerClass: Class[_ <: AtmosphereController],
                       upgradeHeader: Option[String],
                       connectionHeaders: Seq[String]): Option[Handler] = {
    if (!AtmosphereCoordinator.instance.matchPath(requestPath))
      None

    else {
      val controller: AtmosphereController =
        Option(play.api.Play.current.injector.instanceOf(controllerClass))
          .getOrElse(controllerClass.newInstance)

      // Netty fail to decode headers separated by a ','
      val javaAction =
        if (isWsSupported(upgradeHeader, connectionHeaders))
          JavaWebSocket.ofString(controller.webSocket)
        else
          new JavaAction(components) {
            val annotations = new JavaActionAnnotations(controllerClass, controllerClass.getMethod("http"))
            val parser = annotations.parser

            def invocation = Promise.pure(controller.http)
          }

      Some(javaAction)
    }
  }


  def dispatch(request: RequestHeader): Option[Handler] =
    dispatch(request, classOf[AtmosphereController])

  def dispatch(request: RequestHeader, controllerClass: Class[_ <: AtmosphereController]): Option[Handler] = {
    val upgradeHeader = request.headers.get("Upgrade")
    val connectionHeaders = Option(request.headers.get("Connection")).map(_.toSeq).getOrElse(Seq.empty)
    dispatch(request.path, controllerClass, upgradeHeader, connectionHeaders)
  }

}