## Playtosphere: Atmosphere for Play!

This project brings the [Atmosphere Framework](https://github.com/Atmosphere/atmosphere) to the [Play!](http://www.playframework.com/) Framework. It support ALL Atmosphere's modules like Runtime, Jersey and Socket.IO.

## Try it!

Fork the [samples workspace](https://github.com/Atmosphere/atmosphere-samples/tree/master/play-samples) and go under samples/chat or samples/jerseyy)

```bash
   % sbt update
   % sbt run
```

Download [Atmosphere Play!](http://search.maven.org/#search%7Cga%7C1%7Catmosphere-play), use Maven or  [sbt](http://ntcoding.blogspot.ca/2013/09/atmosphere-scala-sbt-akka-step-by-step.html)

For Play 2.2.x+:

```xml
     <dependency>
         <groupId>org.atmosphere</groupId>
         <artifactId>atmosphere-play</artifactId>
         <version>2.1.1</version>
     </dependency>
```

For Play 2.1.x:

```xml
     <dependency>
         <groupId>org.atmosphere</groupId>
         <artifactId>atmosphere-play</artifactId>
         <version>1.0.0</version>
     </dependency>
```

## Join the growing community

If you are interested, subscribe to our [mailing list](http://groups.google.com/group/atmosphere-framework) for more info!  We are on irc.freenode.net under #atmosphere-comet

## WebSockets, Server Sent Events, Streaming and Long-Polling transparently supported!

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

Server side using atmosphere-jersey
```java
 @Path("/chat")
 public class ChatResource {

     /**
      * Suspend the response without writing anything back to the client.
      * @return a white space
      */
     @Suspend(contentType = "application/json")
     @GET
     public String suspend() {
         return "";
     }

     /**
      * Broadcast the received message object to all suspended response. Do not write back the message to the calling connection.
      * @param message a {@link Message}
      * @return a {@link Response}
      */
     @Broadcast(writeEntity = false)
     @POST
     @Produces("application/json")
     public Response broadcast(Message message) {
         return new Response(message.getAuthor(), message.getMessage());
     }
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


[![Analytics](https://ga-beacon.appspot.com/UA-31990725-2/Atmosphere/atmosphere-play)]
