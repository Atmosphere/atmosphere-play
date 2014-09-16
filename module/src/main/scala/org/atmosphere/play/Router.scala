/*
 * Copyright 2012 Jeanfrancois Arcand
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.play

import play.api.mvc.Handler
import play.api.mvc.{RequestHeader=>ScalaRequestHeader}
import play.core.j._
import play.mvc.Http.RequestHeader
import play.libs.F.Promise

object Router {

  private def isWsSupported(upgradeHeader: Option[String], connectionHeaders: Seq[String]): Boolean =
    upgradeHeader.map("websocket".equalsIgnoreCase)
    .getOrElse(connectionHeaders.contains("upgrade".equalsIgnoreCase _))

  private def dispatch(requestPath: String,
                       controllerClass : Class[_<:AtmosphereController],
                       upgradeHeader: Option[String],
                       connectionHeaders: Seq[String]): Option[Handler] =

    if (!AtmosphereCoordinator.instance.matchPath(requestPath))
      None

    else {
      val controller : AtmosphereController =
        Option(play.api.Play.current.global.getControllerInstance(controllerClass))
          .getOrElse(controllerClass.newInstance)

      // Netty fail to decode headers separated by a ','
      val javaAction =
        if (isWsSupported(upgradeHeader, connectionHeaders))
          JavaWebSocket.ofString(controller.webSocket)
        else
          new JavaAction {
            val annotations = new JavaActionAnnotations(controllerClass, controllerClass.getMethod("http"))
            val parser = annotations.parser
            def invocation = Promise.pure(controller.http)
          }

      Some(javaAction)
    }

  def dispatch(request: RequestHeader): Handler =
	  dispatch(request, classOf[AtmosphereController])

  def dispatch(request: RequestHeader, controllerClass : Class[_<:AtmosphereController]): Handler = {

    val upgradeHeader = Option(request.getHeader("Upgrade"))
    val connectionHeaders = Option(request.headers.get("Connection")).map(_.toSeq).getOrElse(Seq.empty)
    dispatch(request.path, controllerClass, upgradeHeader, connectionHeaders).orNull
  }

  def dispatch(request: ScalaRequestHeader): Option[Handler] =
	  dispatch(request, classOf[AtmosphereController])

  def dispatch(request: ScalaRequestHeader, controllerClass : Class[_<:AtmosphereController]): Option[Handler] =
    dispatch(request.path, controllerClass, request.headers.get("Upgrade"), request.headers.getAll("Connection"))
}
