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

import org.atmosphere.container.NettyCometSupport;
import org.atmosphere.cpr.Action;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AsynchronousProcessor;
import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResourceImpl;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.cpr.FrameworkConfig;
import org.atmosphere.cpr.HeaderConfig;
import org.atmosphere.util.EndpointMapper;
import org.atmosphere.util.ExecutorsFactory;
import org.atmosphere.util.ServletProxyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.atmosphere.cpr.HeaderConfig.X_ATMOSPHERE_TRANSPORT;

public class AtmosphereCoordinator {
    private static Logger logger = LoggerFactory.getLogger(AtmosphereCoordinator.class);

    public static String PLAY_SESSION_CONVERTER = "org.atmopshere.play.AtmospherePlaySessionConverter";

    private AtmosphereFramework framework;
    private AsynchronousProcessor asynchronousProcessor;
    public static AtmosphereCoordinator instance = new AtmosphereCoordinator();
    private ScheduledExecutorService suspendTimer;
    private EndpointMapper<AtmosphereFramework.AtmosphereHandlerWrapper> mapper;

    private AtmosphereCoordinator() {
        framework = new AtmosphereFramework();
        asynchronousProcessor = new NettyCometSupport(framework().getAtmosphereConfig());
        framework.setAsyncSupport(asynchronousProcessor);
        suspendTimer = ExecutorsFactory.getScheduler(framework.getAtmosphereConfig());
        mapper = framework.endPointMapper();
    }

    public AtmosphereCoordinator discover(Class<?> clazz) {
        framework.addAnnotationPackage(clazz);
        return this;
    }

    public AtmosphereCoordinator ready() {
        framework().addInitParameter(ApplicationConfig.ALLOW_QUERYSTRING_AS_REQUEST, "false");

        ServletProxyFactory.getDefault().addMethodHandler("getServerInfo", new ServletProxyFactory.MethodHandler() {
            @Override
            public Object handle(Object clazz, Method method, Object[] methodObjects) {
                return "Playtosphere/2.0.0";
            }
        });
        framework().init();
        return this;
    }

    public boolean matchPath(String path) {
        return mapper.map(path, framework().getAtmosphereHandlers()) != null;
    }

    public AtmosphereCoordinator path(String mappingPath) {
        framework.addInitParameter(ApplicationConfig.ATMOSPHERE_HANDLER_MAPPING, mappingPath);
        return this;
    }

    public AtmosphereCoordinator shutdown() {
        framework.destroy();
        return this;
    }

    public static AtmosphereCoordinator instance() {
        if (instance.framework().isDestroyed()) {
            instance = new AtmosphereCoordinator();
        }
        return instance;
    }

    public AtmosphereFramework framework() {
        return framework;
    }

    public boolean route(AtmosphereRequest request, AtmosphereResponse response) throws IOException {
        boolean resumeOnBroadcast = false;
        boolean keptOpen = true;
        boolean skipClose = false;
        final PlayAsyncIOWriter w = (PlayAsyncIOWriter) response.getAsyncIOWriter();

        try {

            Action a = framework.doCometSupport(request, response);
            final AtmosphereResourceImpl impl = (AtmosphereResourceImpl) request.getAttribute(FrameworkConfig.ATMOSPHERE_RESOURCE);

            String transport = (String) request.getAttribute(FrameworkConfig.TRANSPORT_IN_USE);
            if (transport == null) {
                transport = request.getHeader(X_ATMOSPHERE_TRANSPORT);
            }

            if (a.type() == Action.TYPE.SUSPEND) {
                if (transport.equalsIgnoreCase(HeaderConfig.LONG_POLLING_TRANSPORT) || transport.equalsIgnoreCase(HeaderConfig.JSONP_TRANSPORT)) {
                    resumeOnBroadcast = true;
                }
            } else {
                keptOpen = false;
            }

            logger.trace("Transport {} resumeOnBroadcast {}", transport, resumeOnBroadcast);

            final Action action = (Action) request.getAttribute(NettyCometSupport.SUSPEND);
            if (action != null && action.type() == Action.TYPE.SUSPEND && action.timeout() != -1) {
                final AtomicReference<Future<?>> f = new AtomicReference<Future<?>>();
                f.set(suspendTimer.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        if (!w.isClosed() && (System.currentTimeMillis() - w.lastTick()) > action.timeout()) {
                            asynchronousProcessor.endRequest(impl, false);
                            f.get().cancel(true);
                        }
                    }
                }, action.timeout(), action.timeout(), TimeUnit.MILLISECONDS));
            } else if (action != null && action.type() == Action.TYPE.RESUME) {
                resumeOnBroadcast = false;
            }
            w.resumeOnBroadcast(resumeOnBroadcast);
        } catch (Throwable e) {
            logger.error("Unable to process request", e);
            keptOpen = false;
        } finally {
            if (w != null && !resumeOnBroadcast && !keptOpen) {
                if (!skipClose) {
                    w.close(null);
                }
            }
        }
        return keptOpen;
    }
}
