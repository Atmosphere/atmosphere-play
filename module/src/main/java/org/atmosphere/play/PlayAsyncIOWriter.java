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

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.Status;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import org.atmosphere.cpr.AsyncIOWriter;
import org.atmosphere.cpr.AsynchronousProcessor;
import org.atmosphere.cpr.AtmosphereInterceptorWriter;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResourceImpl;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.cpr.AtmosphereResponseImpl;
import org.atmosphere.cpr.HeaderConfig;
import org.atmosphere.util.ByteArrayAsyncWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Http;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayAsyncIOWriter extends AtmosphereInterceptorWriter implements PlayInternal<Source<ByteString, ?>> {
    public static final int BUFFER_SIZE = 256;
    private static final Logger logger = LoggerFactory.getLogger(PlayAsyncIOWriter.class);
    private final AtomicInteger pendingWrite = new AtomicInteger();
    private final AtomicBoolean asyncClose = new AtomicBoolean(false);
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final ByteArrayAsyncWriter buffer = new ByteArrayAsyncWriter();
    private Source<ByteString,? > source;
    private OutStream out;
    private boolean byteWritten = false;
    private long lastWrite = 0;
    private boolean resumeOnBroadcast;

    public PlayAsyncIOWriter(final Http.Request request, final Map<String, Object> additionalAttributes, final Http.Response response) {
        final String[] transport = request.queryString() != null ? request.queryString().get(HeaderConfig.X_ATMOSPHERE_TRANSPORT) : null;
        this.source = Source.<ByteString>actorRef(BUFFER_SIZE, OverflowStrategy.dropNew()).mapMaterializedValue(actorRef -> {
                   out = new OutStream() {
                       @Override
                       public void write(String message) {
                           actorRef.tell(ByteString.fromString(message), ActorRef.noSender());
                           byteWritten = true;
                       }

                       @Override
                       public void close() {
                           actorRef.tell(new Status.Success(200), ActorRef.noSender());
                           byteWritten = false;
                       }
                   };
                   boolean keepAlive = false;

                   try {
                       final AtmosphereRequest r = AtmosphereUtils.request(request, additionalAttributes);

                       AtmosphereResponse res = new AtmosphereResponseImpl.Builder()
                               .asyncIOWriter(PlayAsyncIOWriter.this)
                               .writeHeader(false)
                               .request(r).build();
                       keepAlive = AtmosphereCoordinator.instance().route(r, res);
                   } catch (Throwable e) {
                       logger.error("", e);
                       keepAlive = true;
                   } finally {
                       if (!keepAlive) {
                           out.close();
                       }
                   }

                   return NotUsed.getInstance();
               })
                .watchTermination((arg1, arg2) -> {
           try {
               if (transport != null && transport.length > 0 && !transport[0].equalsIgnoreCase(HeaderConfig.POLLING_TRANSPORT)) {
                   _close(AtmosphereUtils.request(request, additionalAttributes));
               }
           } catch (Throwable ignored) {
           }
           return NotUsed.getInstance();
       });

        // TODO: Configuring headers in Atmosphere won't work as the onReady is asynchronously called.
        // TODO: Some Broadcaster's Cache won't work as well.
        if (transport != null && transport.length > 0 && transport[0].equalsIgnoreCase(HeaderConfig.SSE_TRANSPORT)) {
            response.setContentType("text/event-stream");
        }
    }

    public Source<ByteString, ?> internal() {
        return source;
    }

    public boolean isClosed() {
        return isClosed.get();
    }

    public boolean byteWritten() {
        return byteWritten;
    }

    public void resumeOnBroadcast(boolean resumeOnBroadcast) {
        this.resumeOnBroadcast = resumeOnBroadcast;
    }

    @Override
    public AsyncIOWriter writeError(AtmosphereResponse r, int errorCode, String message) throws IOException {
//        if (!response.isOpen()) {
//            return this;
//        }

        // TODO: Set status
        logger.error("Error {}:{}", errorCode, message);
        out.write(message);
        return this;
    }

    @Override
    public AsyncIOWriter write(AtmosphereResponse r, String data) throws IOException {
        byte[] b = data.getBytes("ISO-8859-1");
        write(r, b);
        return this;
    }

    @Override
    public AsyncIOWriter write(AtmosphereResponse r, byte[] data) throws IOException {
        write(r, data, 0, data.length);
        return this;
    }

    protected byte[] transform(AtmosphereResponse response, byte[] b, int offset, int length) throws IOException {
        AsyncIOWriter a = response.getAsyncIOWriter();
        try {
            response.asyncIOWriter(buffer);
            invokeInterceptor(response, b, offset, length);
            return buffer.stream().toByteArray();
        } finally {
            buffer.close(null);
            response.asyncIOWriter(a);
        }
    }

    @Override
    public AsyncIOWriter write(final AtmosphereResponse r, byte[] data, int offset, int length) throws IOException {
        logger.trace("Writing {} with transport {}", r.resource().uuid(), r.resource().transport());
        boolean transform = filters.size() > 0 && r.getStatus() < 400;
        if (transform) {
            data = transform(r, data, offset, length);
            offset = 0;
            length = data.length;
        }

        pendingWrite.incrementAndGet();

        out.write(new String(data, offset, length, r.getCharacterEncoding()));
        byteWritten = true;
        lastWrite = System.currentTimeMillis();
        if (resumeOnBroadcast) {
            out.close();
            _close(r.request());
        }
        return this;
    }

    private void _close(AtmosphereRequest request) {
        AtmosphereResourceImpl r = AtmosphereResourceImpl.class.cast(request.resource());
        if (request != null && r != null) {
            AsynchronousProcessor.class.cast(r.getAtmosphereConfig().framework().getAsyncSupport()).endRequest(r, true);
        }
    }

    public long lastTick() {
        return lastWrite == -1 ? System.currentTimeMillis() : lastWrite;
    }

    @Override
    public void close(AtmosphereResponse r) throws IOException {
        // Make sure we don't have bufferred bytes
        if (!byteWritten && r != null && r.getOutputStream() != null) {
            r.getOutputStream().flush();
        }

        asyncClose.set(true);
        if (byteWritten) {
            out.close();
        }
    }
}
