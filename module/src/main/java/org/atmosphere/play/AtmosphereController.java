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
package org.atmosphere.play;

import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.WebSocket;

public class AtmosphereController extends Controller {
    private final AtmosphereFramework framework;
    private final AtmosphereConfig config;

    public AtmosphereController() {
        framework = AtmosphereCoordinator.instance().framework();
        config = framework.getAtmosphereConfig();
    }

    public WebSocket<String> webSocket() throws Throwable {
        return new PlayWebSocket(config, request()).internal();
    }


    public Result http() throws Throwable {
        // TODO: Wrong status code on error!
        return ok(new PlayAsyncIOWriter(request(), response()).internal());
    }

}
