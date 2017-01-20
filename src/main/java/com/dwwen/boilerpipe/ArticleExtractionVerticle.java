package com.dwwen.boilerpipe;

import de.l3s.boilerpipe.BoilerpipeExtractor;
import de.l3s.boilerpipe.document.Media;
import de.l3s.boilerpipe.extractors.CommonExtractors;
import de.l3s.boilerpipe.sax.HTMLDocument;
import de.l3s.boilerpipe.sax.MediaExtractor;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.sockjs.impl.JsonCodec;
import org.vertx.java.platform.Verticle;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.List;


public class ArticleExtractionVerticle extends Verticle {

    public void start() {

        container.deployVerticle("com.dwwen.boilerpipe.HttpFetcherVerticle");

        final EventBus eb = vertx.eventBus();
        final Logger log = container.logger();

        vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
            public void handle(final HttpServerRequest request) {
                if (request.method().equalsIgnoreCase("POST")) {

                    request.expectMultiPart(true);

                    request.bodyHandler(new Handler<Buffer>() {
                        public void handle(Buffer body) {
                            // The entire body has now been received
                            log.info("The total body received was " + body.length() + " bytes");
                        }
                    });

                    request.endHandler(new VoidHandler() {
                        public void handle() {
                            // The request has been all ready so now we can look at the form attributes
                            MultiMap attrs = request.formAttributes();
                            final String strUrl = attrs.get("url");
                            final String responseType = attrs.get("responseType");

                            eb.sendWithTimeout("http.fetcher", strUrl, 60000, new Handler<AsyncResult<Message<JsonObject>>>() {
                                int redirects = 0;

                                public void handle(AsyncResult<Message<JsonObject>> result) {
                                    if (result.succeeded()) {
                                        JsonObject httpResp = result.result().body();
                                        int statusCode = httpResp.getNumber("StatusCode").intValue();
                                        if ((statusCode == 301 || statusCode == 302) && redirects < 20) {
                                            redirects++;
                                            String location = httpResp.getString("Location");
                                            eb.sendWithTimeout("http.fetcher", location, 60000, this);
                                        } else {
                                            String responseBody = null;

                                            try {

                                                final BoilerpipeExtractor extractor = CommonExtractors.ARTICLE_EXTRACTOR;
                                                String body = httpResp.getString("Body");
                                                if ("text".equalsIgnoreCase(responseType)) {

                                                    responseBody = extractor.getText(body);

                                                }
                                                else  if("media".equalsIgnoreCase(responseType)){
                                                    final MediaExtractor mediaExtractor = MediaExtractor.INSTANCE;
                                                    List<Media> media = mediaExtractor.process(body, extractor);
                                                    responseBody = JsonCodec.encode(media);
                                                }
                                                else {
                                                    final HtmlArticleExtractor htmlExtr = HtmlArticleExtractor.INSTANCE;
                                                    HTMLDocument htmlDocument = new HTMLDocument(
                                                            body.getBytes(Charset.forName("UTF-8")),
                                                            Charset.forName("UTF-8"));
                                                    URI uri = new URI(strUrl);
                                                    responseBody = htmlExtr.process(htmlDocument, uri, extractor);
                                                }

                                                request.response()
                                                        .setStatusCode(statusCode)
                                                        .putHeader("Content-Length", responseBody.getBytes(Charset.forName("UTF-8")).length + "")
                                                        .putHeader("Content-Type", "text/html; charset=UTF-8")
                                                        .write(responseBody)
                                                        .end();
                                            }
                                            catch (Exception e){
                                                    e.printStackTrace();
                                                    request.response()
                                                    .setStatusCode(500)
                                                    .putHeader("Content-Length", 13 + "")
                                                    .write("unknown error")
                                                    .end();
                                            }
                                        }

                                        }else{

                                            request.response()
                                                    .setStatusCode(500)
                                                    .putHeader("Content-Length", 28 + "")
                                                    .write("timeout, could not fetch url")
                                                    .end();
                                        }
                                    }
                                }

                                );
                            }
                        }

                        );
                    }else{
                        request.response()
                                .setStatusCode(405)
                                .setStatusMessage("Method Not Allowed")
                                .putHeader("Content-Length", 18 + "")
                                .write("only POST requests")
                                .end();
                    }
                }
            }

            ).

            listen(8090,"0.0.0.0");
        }
    }
