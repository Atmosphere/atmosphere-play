package org.atmosphere.play

import java.util.concurrent.CompletableFuture

import com.google.inject.Inject
import play.api.http._
import play.api.inject.Injector
import play.api.mvc.{Handler, RequestHeader}
import play.api.routing.Router
import play.core.j._
import play.api.mvc.BodyParser
import play.core.routing.HandlerInvokerFactory
import play.mvc.Http.RequestBody
import scala.concurrent.ExecutionContext.Implicits.global


class AtmosphereHttpRequestHandler @Inject()(components: JavaHandlerComponents,
                                             router: Router,
                                             errorHandler: HttpErrorHandler,
                                             configuration: HttpConfiguration,
                                             filters: HttpFilters,
                                             injector: Injector)
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
        Option(injector.instanceOf(controllerClass)).getOrElse(controllerClass.newInstance)

      // Netty fail to decode headers separated by a ','
      val javaAction :Handler =
        if (isWsSupported(upgradeHeader, connectionHeaders))
          controller.webSocket
        else
          controller.http

      Some(javaAction)
    }
  }

  def javaBodyParserToScala(parser: play.mvc.BodyParser[_]): BodyParser[RequestBody] = BodyParser { request =>
    val accumulator = parser.apply(new play.core.j.RequestHeaderImpl(request)).asScala()
    accumulator.map { javaEither =>
      if (javaEither.left.isPresent) {
        Left(javaEither.left.get().asScala())
      } else {
        Right(new RequestBody(javaEither.right.get()))
      }
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