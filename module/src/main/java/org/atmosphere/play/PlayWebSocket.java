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
import akka.actor.Status;
import org.atmosphere.cpr.AtmosphereConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayWebSocket extends org.atmosphere.websocket.WebSocket {
    private static final Logger logger = LoggerFactory.getLogger(PlayWebSocket.class);

    private OutStream out;

    private final AtomicBoolean firstWrite = new AtomicBoolean(false);

    public PlayWebSocket(ActorRef actorRef, final AtmosphereConfig config) {
        super(config);
        out  = new OutStream() {
            @Override
            public void write(String message) {
                actorRef.tell(message, ActorRef.noSender());
            }

            @Override
            public void close() {
            	actorRef.tell(new Status.Success(200), ActorRef.noSender());
            }
        };
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.atmosphere.websocket.WebSocket write(String data) throws IOException {
        firstWrite.set(true);
        if (!isOpen()) throw new IOException("Connection remotely closed");

        logger.trace("WebSocket.write()");
        out.write(data);
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

        out.write(new String(data, offset, length, "UTF-8"));
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        out.close();
    }
}
