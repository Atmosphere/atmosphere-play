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

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.util.ByteString;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResponseImpl;
import org.atmosphere.cpr.WebSocketProcessorFactory;
import org.atmosphere.websocket.WebSocketProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import play.libs.F;
import play.mvc.Http;
//import play.mvc.LegacyWebSocket;
import play.mvc.WebSocket;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class PlayWebSocket extends org.atmosphere.websocket.WebSocket {
    private static final Logger logger = LoggerFactory.getLogger(PlayWebSocket.class);
//    private WebSocket.Out<String> out;
//    private WebSocket.In<String> in;

    private ActorRef actorRef;
    private final AtomicBoolean firstWrite = new AtomicBoolean(false);

    public PlayWebSocket(ActorRef actorRef, final AtmosphereConfig config) {
        super(config);
        this.actorRef = actorRef;

//        w = new LegacyWebSocket<String>() {
//            @Override
//            public void onReady(WebSocket.In<String> iin, WebSocket.Out<String> oout) {
////                out = oout;
////                in = iin;
//                in.onClose(() -> webSocketProcessor.close(PlayWebSocket.this, 1002));
//                in.onMessage(message -> webSocketProcessor.invokeWebSocketProtocol(PlayWebSocket.this, message));
//                AtmosphereRequest r = null;
//                try {
//                    r = AtmosphereUtils.request(request, additionalAttributes);
//                } catch (Throwable t) {
//                    logger.error("", t);
//                }
//
//                try {
//                    webSocketProcessor.open(PlayWebSocket.this, r, AtmosphereResponseImpl.newInstance(config, r, PlayWebSocket.this));
//                } catch (IOException e) {
//                    logger.error("", e);
//                    out.close();
//                }
//            }
//        };
    }

//    public LegacyWebSocket<String> internal() {
//        return w;
//    }


    @Override
    public boolean isOpen() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.atmosphere.websocket.WebSocket write(String data) throws IOException {
        firstWrite.set(true);
        if (!isOpen()) throw new IOException("Connection remotely closed");

        logger.trace("WebSocket.write()");
       // out.write(data);
        this.actorRef.tell(ByteString.fromString(data), ActorRef.noSender());
        lastWrite = System.currentTimeMillis();
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.atmosphere.websocket.WebSocket write(byte[] data, int offset, int length) throws IOException {
        firstWrite.set(true);
        if (!isOpen()) throw new IOException("Connection remotely closed");

       // out.write(new String(data, offset, length, "UTF-8"));

        this.actorRef.tell(new String(data, offset, length, "UTF-8"), ActorRef.noSender());
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        //out.close();
        this.actorRef.tell(PoisonPill.getInstance(), ActorRef.noSender());
    }
}


