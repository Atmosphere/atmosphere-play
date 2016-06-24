/*
 * Copyright 2015 Async-IO.org
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

import org.apache.commons.lang3.StringUtils;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.util.IOUtils;
import play.mvc.Controller;
import play.mvc.LegacyWebSocket;
import play.mvc.Result;

import java.util.Collections;
import java.util.Map;

public class AtmosphereController extends Controller {
    private final AtmosphereFramework framework;
    private final AtmosphereConfig config;
    private final AtmospherePlaySessionConverter converter;

    @SuppressWarnings("unchecked")
	public AtmosphereController() throws InstantiationException, IllegalAccessException, Exception {
        framework = AtmosphereCoordinator.instance().framework();
        config = framework.getAtmosphereConfig();

        final String playSessionConverter = config.getInitParameter(AtmosphereCoordinator.PLAY_SESSION_CONVERTER);
        if(StringUtils.isNotBlank(playSessionConverter)){
        	converter = framework.newClassInstance(AtmospherePlaySessionConverter.class,
	                                (Class<AtmospherePlaySessionConverter>) IOUtils.loadClass(getClass(), playSessionConverter));
        } else {
        	converter = null;
        }
    }

    public LegacyWebSocket<String> webSocket() throws Throwable {
        return new PlayWebSocket(config, request(), convertedSession()).internal();
    }

    public Result http() throws Throwable {
        // TODO: Wrong status code on error!
        return ok(new PlayAsyncIOWriter(request(), convertedSession(), response()).internal());
    }

    protected Map<String, Object> convertedSession() {
    	final Map<String, Object> result;
    	if( converter != null ){
    		result = converter.convertToAttributes(session());
    	} else {
    		result = Collections.emptyMap();
    	}

    	return result;
    }

}
