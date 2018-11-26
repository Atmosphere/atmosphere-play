package org.atmosphere.play;

import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResponseImpl;
import org.atmosphere.cpr.WebSocketProcessorFactory;
import org.atmosphere.websocket.WebSocketProcessor;
import play.Logger;
import play.api.mvc.RequestHeader;
import play.mvc.Http;

import java.util.Map;

public class AtmosphereWebsocketActor extends AbstractActor {
	private static final Logger.ALogger LOG = Logger.of(AtmosphereWebsocketActor.class);
	private PlayWebSocket playWebSocket = null;
	private WebSocketProcessor webSocketProcessor = null;
	private ActorRef actorRef;
	private RequestHeader requestHeader;
	private Map<String, Object> additionalAttributes;
	private  AtmosphereConfig atmosphereConfig;

	public static Props props(ActorRef actorRef, RequestHeader requestHeader, scala.collection.immutable.Map<String, String> additionalAtts, AtmosphereConfig config) {
		return Props.create(AtmosphereWebsocketActor.class, actorRef, requestHeader, additionalAtts, config);
	}

	public AtmosphereWebsocketActor(ActorRef actorRef, RequestHeader requestHeader, Map<String, Object> additionalAttributes, AtmosphereConfig atmosphereConfig) {
		this.actorRef = actorRef;
		this.requestHeader = requestHeader;
		this.additionalAttributes = additionalAttributes;
		this.atmosphereConfig = atmosphereConfig;
	}

	@Override
	public void preStart() throws Exception {
		self().tell(new StartMessasge(), sender());
	}

	@Override
	public Receive createReceive() {
		return receiveBuilder().match(StartMessasge.class, this::handleStartMessage).build();
	}

	private void handleStartMessage(StartMessasge startMessasge) {
		try {
			this.playWebSocket = new PlayWebSocket(actorRef, atmosphereConfig);
			this.webSocketProcessor = WebSocketProcessorFactory.getDefault().getWebSocketProcessor(atmosphereConfig.framework());
			AtmosphereRequest atmosphereRequest = AtmosphereUtils.request(new Http.RequestImpl(requestHeader), additionalAttributes);
			this.webSocketProcessor.open(playWebSocket, atmosphereRequest, AtmosphereResponseImpl.newInstance(atmosphereConfig, atmosphereRequest, playWebSocket));
		} catch (Throwable throwable) {
			LOG.error("", throwable);
		}
	}

	public static final class StartMessasge {
	}

}
