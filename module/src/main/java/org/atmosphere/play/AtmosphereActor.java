package org.atmosphere.play;

import akka.actor.AbstractActor;
import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.IllegalActorStateException;
import akka.actor.Props;
import akka.japi.pf.ReceiveBuilder;
import akka.util.ByteString;
import play.Logger;
import play.api.mvc.RequestHeader;
import play.libs.akka.InjectedActorSupport;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

import java.util.Map;

public class AtmosphereActor extends AbstractActor implements InjectedActorSupport  {
	private static final Logger.ALogger LOG = Logger.of(AtmosphereActor.class);

	public static Props props(ActorRef actorRef, RequestHeader requestHeader, scala.collection.immutable.Map<String, String> attditionalAttributes) {
		return Props.create(AtmosphereActor.class, actorRef, requestHeader, attditionalAttributes);
	}

	private ActorRef actorRef;
	private Map<String, Object> additionalAttributes;
	private RequestHeader requestHeader;
	private PlayAsyncIOWriter playAsyncIOWriter;

	public AtmosphereActor(ActorRef actorRef, RequestHeader requestHeader, Map<String, Object> attditionalAttributes) {
		this.actorRef = actorRef;
		this.requestHeader = requestHeader;
		this.additionalAttributes = additionalAttributes;
		this.playAsyncIOWriter =  new PlayAsyncIOWriter(actorRef, requestHeader, additionalAttributes);
	}


	@Override
	public Receive createReceive() {
		return receiveBuilder().match(
				ByteString.class, this::handleByteString
		).build();
	}

	private void handleByteString(ByteString byteString) {
		LOG.info("[{}] tell sender() ByteString", AtmosphereActor.class.toString());
		actorRef.tell(byteString, sender());
	}

}
