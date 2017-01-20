package com.dwwen.boilerpipe;

import org.apache.http.entity.ContentType;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Verticle;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by abdulaziz on 11/22/2014.
 */

public class HttpFetcherVerticle extends Verticle {

    public void start() {

        final EventBus eb = vertx.eventBus();
        final Logger log = container.logger();

        Handler<Message<String>> httpHandler = new Handler<Message<String>>() {
            public void handle(final Message<String> message) {
                String strUrl = message.body();

                URL url = null;
                try {
                    url = new URL(strUrl);

                } catch (MalformedURLException e) {
                    JsonObject resp = new JsonObject();
                    resp.putNumber("StatusCode", 401);
                    resp.putString("Content-Length", 11 + "");
                    resp.putString("body", "invalid url");
                    message.reply(resp);
                }

                HttpClient client = vertx.createHttpClient().setHost(url.getHost());

                HttpClientRequest req = client.get(url.getFile(), new Handler<HttpClientResponse>() {
                    public void handle(final HttpClientResponse resp) {
                        log.info("Got a response: " + resp.statusCode());
                            resp.bodyHandler(new Handler<Buffer>() {
                                public void handle(Buffer body) {
                                    // The entire response body has been received
                                    log.info("The total body received was " + body.length() + " bytes");
                                    String charset;
                                    try{
                                        ContentType contentType = ContentType.parse(resp.headers().get("Content-Type"));
                                        charset = contentType.getCharset().name();
                                    }
                                    catch (Exception e){
                                        charset = "UTF-8";
                                    }
                                    String htmlStr = body.toString(charset);
                                    JsonObject ebResp = new JsonObject();
                                    ebResp.putNumber("StatusCode", resp.statusCode());
                                    ebResp.putString("ContentLength", body.length()+"");
                                    ebResp.putString("Location", resp.headers().get("Location"));
                                    ebResp.putString("Body", htmlStr);
                                    message.reply(ebResp);
                                }
                            });
                    }
                });

                req.end();
            }
        };

        eb.registerHandler("http.fetcher", httpHandler);

    }
}
