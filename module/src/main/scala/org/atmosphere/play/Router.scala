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
import play.core.j._
import play.mvc.Http.RequestHeader
import org.apache.commons.lang3.reflect.MethodUtils

object Router {

  def dispatch(request: RequestHeader): Handler = {
    if (!AtmosphereCoordinator.instance().matchPath(request.path)) {
      return null;
    }
    val c = classOf[AtmosphereController]
    val a = new AtmosphereController

    // Netty fail to decode headers separated by a ','
    val connectionH: Array[String] = request.headers().get("Connection")
    val webSocketH = request.getHeader("Upgrade")
    var wsSupported = false;

    if (webSocketH != null && webSocketH.equalsIgnoreCase(webSocketH)) {
      wsSupported = true
    }

    if (!wsSupported && connectionH != null) {
    for (c: String <- connectionH) {
      if (c != null && c.toLowerCase().equalsIgnoreCase("upgrade")) {
        wsSupported = true;
      }
    }
    }

    if (wsSupported) {
      JavaWebSocket.ofString(a.webSocket)
    } else {
      new JavaAction {
        def invocation = a.http

        val controller = c
        lazy val method = MethodUtils.getMatchingAccessibleMethod(controller, "http")
      }
    }

  }

  import play.api.mvc.{RequestHeader=>ScalaRequestHeader}
 
  def dispatch(request: ScalaRequestHeader): Option[Handler] = {
    if (!AtmosphereCoordinator.instance().matchPath(request.path)) {
      None
    } else {
      val c = classOf[AtmosphereController]
      val a = new AtmosphereController

      // Netty fail to decode headers separated by a ','
      val connectionH = request.headers.get("Connection")
      val webSocketH = request.headers.get("Upgrade")
      val wsSupported = webSocketH.isDefined || connectionH.map(_.toLowerCase.contains("upgrade")).getOrElse(false)

      if (wsSupported) {
        Some(JavaWebSocket.ofString(a.webSocket))
      } else {
        Some(new JavaAction {
          def invocation = a.http

          val controller = c
          lazy val method = MethodUtils.getMatchingAccessibleMethod(controller, "http")
        }
        )
      }
    }
  }

}
