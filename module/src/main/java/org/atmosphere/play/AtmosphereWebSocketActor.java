package org.atmosphere.play;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.util.ByteString;
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
	private ActorRef actorRef;
	private RequestHeader requestHeader;
	private Map<String, Object> additionalAttributes;
	private  AtmosphereConfig atmosphereConfig;

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
			this.playWebSocket = new PlayWebSocket(actorRef, atmosphereConfig);
			this.webSocketProcessor = WebSocketProcessorFactory.getDefault().getWebSocketProcessor(atmosphereConfig.framework());
			AtmosphereRequest atmosphereRequest = AtmosphereUtils.request(new Http.RequestImpl(requestHeader), additionalAttributes);
			this.webSocketProcessor.open(playWebSocket, atmosphereRequest, AtmosphereResponseImpl.newInstance(atmosphereConfig, atmosphereRequest, playWebSocket));
		} catch (Throwable throwable) {
			LOG.error("Failed to start the actor ", throwable);
		}
	}

	@Override
	public void postStop() {
		this.webSocketProcessor.close(playWebSocket, 1002);
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder()
				.match(String.class, this::handleString)
				.match(ByteString.class, this::handleByteString)
				.build();
	}

	private void handleString(String string) {
		this.webSocketProcessor.invokeWebSocketProtocol(this.playWebSocket, string);
	}

	private void handleByteString(ByteString byteString) {
		this.webSocketProcessor.invokeWebSocketProtocol(this.playWebSocket, byteString.toString());
	}

}
