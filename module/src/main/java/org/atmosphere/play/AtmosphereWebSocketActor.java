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

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.util.ByteString;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResponseImpl;
import org.atmosphere.cpr.WebSocketProcessorFactory;
import org.atmosphere.websocket.WebSocketProcessor;
import play.Logger;
import play.api.mvc.RequestHeader;
import play.mvc.Http;

import java.util.Map;

public class AtmosphereWebSocketActor extends AbstractActor {
	private static final Logger.ALogger LOG = Logger.of(AtmosphereWebSocketActor.class);
	private PlayWebSocket playWebSocket = null;
	private WebSocketProcessor webSocketProcessor = null;
	private final ActorRef actorRef;
	private final RequestHeader requestHeader;
	private final Map<String, Object> additionalAttributes;
	private final AtmosphereConfig atmosphereConfig;

	public static Props props(ActorRef actorRef, RequestHeader requestHeader, Map<String, Object> additionalAtts, AtmosphereConfig config) {
		return Props.create(AtmosphereWebSocketActor.class, actorRef, requestHeader, additionalAtts, config);
	}

	public AtmosphereWebSocketActor(ActorRef actorRef, RequestHeader requestHeader, Map<String, Object> additionalAttributes, AtmosphereConfig atmosphereConfig) {
		this.actorRef = actorRef;
		this.requestHeader = requestHeader;
		this.additionalAttributes = additionalAttributes;
		this.atmosphereConfig = atmosphereConfig;
	}

	@Override
	public void preStart() {
		try {
			playWebSocket = new PlayWebSocket(actorRef, atmosphereConfig);
			webSocketProcessor = WebSocketProcessorFactory.getDefault().getWebSocketProcessor(atmosphereConfig.framework());
			
			Http.Request javaRequest = new Http.RequestBuilder()
					.method(requestHeader.method())
					.uri(requestHeader.uri())
					.headers(requestHeader.headers().asJava())
					//.bodyRaw(requestHeader.body().asRaw().asBytes().toArray())
					.build();


			AtmosphereRequest atmosphereRequest = AtmosphereUtils.request(javaRequest, additionalAttributes);
			webSocketProcessor.open(playWebSocket, atmosphereRequest, AtmosphereResponseImpl.newInstance(atmosphereConfig, atmosphereRequest, playWebSocket));
		} catch (Throwable throwable) {
			LOG.error("Failed to start the actor ", throwable);
		}
	}

	@Override
	public void postStop() {
		webSocketProcessor.close(playWebSocket, 1004);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(String.class, this::handleString)
				.match(ByteString.class, this::handleByteString)
				.build();
	}

	private void handleString(String string) {
		webSocketProcessor.invokeWebSocketProtocol(playWebSocket, string);
	}

	private void handleByteString(ByteString byteString) {
		webSocketProcessor.invokeWebSocketProtocol(playWebSocket, byteString.toString());
	}

}
