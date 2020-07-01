package se.kry.codetest;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.client.WebClient;

import java.util.List;
import java.util.Set;

public class BackgroundPoller {
    private Logger logger = LoggerFactory.getLogger(BackgroundPoller.class);

    public Future<List<String>> pollServices(Vertx vertx, DBConnector connector, Set<Service> services) {
        logger.info("Poller is called : " + services.size());
        services.forEach(service -> {
          logger.info("polling service url "+ service.getUrl());
            Promise<Void> promise = Promise.promise();
            vertx.executeBlocking(execute -> {
                pollService(service, vertx, connector);
                execute.complete();
            }, false, result -> {
                promise.complete();
              logger.info("Completed polling service url "+ service.getUrl());
            });
            promise.future();
        });
        return Future.succeededFuture();
    }

    private void pollService(Service service, Vertx vertx, DBConnector connector) {
        WebClient client = WebClient.create(vertx);
        client.getAbs(service.getUrl()).send(response -> {
            if (response.failed()) {
                connector.updateService(new JsonArray().add(service.getUrl()).add("FAIL").add(service.getName()));
            } else {
                connector.updateService(new JsonArray().add(service.getUrl()).add(response.result().statusCode() == 200 ? "OK" : "FAIL").add(service.getName()));
            }
        });
    }
}
