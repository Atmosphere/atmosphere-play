package org.atmosphere.play;

import java.util.Map;

import play.mvc.Http;

public interface AtmospherePlaySessionConverter {

	Map<String, Object> convertToAttributes(final Http.Session session);
}
