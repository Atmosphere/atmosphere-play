## Playtosphere: Atmosphere for Play!

This project brings the [Atmosphere Framework](https://github.com/Atmosphere/atmosphere) to the [Play!](http://www.playframework.com/) Framework. It support ALL Atmosphere's modules like Runtime, Jersey and Socket.IO.

For Play 3.0+ and Atmosphere 3.0+:

```xml
     <dependency>
         <groupId>org.atmosphere</groupId>
         <artifactId>atmosphere-play</artifactId>
         <version>3.0.0</version>
     </dependency>
```

For Play 2.8.x+ and Atmosphere 2.7+:

```xml
     <dependency>
         <groupId>org.atmosphere</groupId>
         <artifactId>atmosphere-play</artifactId>
         <version>2.6.0</version>
     </dependency>
```

Server side using atmosphere-runtime
```java
@ManagedService(path = "/chat")
public class Chat {
    private final Logger logger = LoggerFactory.getLogger(Chat.class);

    /**
     * Invoked when the connection as been fully established and suspended, e.g ready for receiving messages.
     *
     * @param r
     */
    @Ready
    public void onReady(final AtmosphereResource r) {
        logger.info("Browser {} connected.", r.uuid());
    }

    /**
     * Invoked when the client disconnect or when an unexpected closing of the underlying connection happens.
     *
     * @param event
     */
    @Disconnect
    public void onDisconnect(AtmosphereResourceEvent event) {
        if (event.isCancelled()) {
            logger.info("Browser {} unexpectedly disconnected", event.getResource().uuid());
        } else if (event.isClosedByClient()) {
            logger.info("Browser {} closed the connection", event.getResource().uuid());
        }
    }

    /**
     * Simple annotated class that demonstrate how {@link org.atmosphere.config.managed.Encoder} and {@link org.atmosphere.config.managed.Decoder
     * can be used.
     *
     * @param message an instance of {@link Message}
     * @return
     * @throws IOException
     */
    @org.atmosphere.config.service.Message(encoders = {JacksonEncoder.class}, decoders = {JacksonDecoder.class})
    public Message onMessage(Message message) throws IOException {
        logger.info("{} just send {}", message.getAuthor(), message.getMessage());
        return message;
    }
```
and on the client side,
```js
    $(function () {
        "use strict";

        var header = $('#header');
        var content = $('#content');
        var input = $('#input');
        var status = $('#status');
        var myName = false;
        var author = null;
        var logged = false;
        var socket = $.atmosphere;
        var subSocket;
        var transport = 'websocket';

        // We are now ready to cut the request
        var request = { url: document.location.toString() + 'chat',
            contentType : "application/json",
            logLevel : 'debug',
            transport : transport ,
            trackMessageLength : true,
            fallbackTransport: 'websocket'};


        request.onMessage = function (response) {

             // do something
        };

        request.onClose = function(response) {
        };

        request.onError = function(response) {
        };

        subSocket = socket.subscribe(request);
```
