/*
 * Copyright 2008-2022 Async-IO.org
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

import akka.actor.ActorSystem;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import org.apache.commons.lang3.StringUtils;
import org.atmosphere.config.service.Singleton;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.HeaderConfig;
import org.atmosphere.util.IOUtils;
import play.api.libs.streams.ActorFlow;
import play.api.mvc.WebSocket;
import play.api.mvc.WebSocket$;
import play.libs.concurrent.HttpExecutionContext;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class AtmosphereController extends Controller {
	public static final int BUFFER_SIZE = 10;
	private final AtmosphereFramework framework;
    private final AtmosphereConfig config;
    private final AtmospherePlaySessionConverter converter;
	private HttpExecutionContext httpExecutionContext;

	@Inject
	private ActorSystem actorSystem;

	@Inject
	private Materializer materializer;

    @Inject
	public AtmosphereController(HttpExecutionContext ec) throws Exception {
    	this.httpExecutionContext = ec;
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

    public WebSocket webSocket(Http.RequestHeader httpRequest) {
    	return WebSocket$.MODULE$.accept(
    			request -> ActorFlow.actorRef(
    					req -> AtmosphereWebSocketActor.props(req, request, convertedSession(httpRequest.session()), config), BUFFER_SIZE, OverflowStrategy.dropNew(), actorSystem , materializer),
				WebSocket.MessageFlowTransformer$.MODULE$.stringMessageFlowTransformer());
    }

    public CompletionStage<Result> http(Http.RequestHeader httpRequest) {
		final String[] transport = httpRequest.queryString() != null ? httpRequest.queryString().get(HeaderConfig.X_ATMOSPHERE_TRANSPORT) : null;
		return CompletableFuture.supplyAsync(() -> {
			Result chunked = Results.ok().chunked(new PlayAsyncIOWriter((Http.Request) httpRequest, convertedSession(httpRequest.session())).internal());
			// TODO: Configuring headers in Atmosphere won't work as the onReady is asynchronously called.
			// TODO: Some Broadcaster's Cache won't work as well.
			if (transport != null && transport.length > 0 && transport[0].equalsIgnoreCase(HeaderConfig.SSE_TRANSPORT)) {
				chunked.as("text/event-stream");
			}
			return chunked;
		}, httpExecutionContext.current());

    }

    protected Map<String, Object> convertedSession(Http.Session session) {
    	final Map<String, Object> result;
    	if( converter != null ){
    		result = converter.convertToAttributes(session);
    	} else {
    		result = Collections.emptyMap();
    	}

    	return result;
    }
}
