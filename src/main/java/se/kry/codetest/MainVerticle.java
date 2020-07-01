package se.kry.codetest;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MainVerticle extends AbstractVerticle {
    private Logger logger = LoggerFactory.getLogger(BackgroundPoller.class);
    private static final String URL_REGEX = "^((((https?|ftps?|gopher|telnet|nntp)://)|(mailto:|news:))" +
            "(%[0-9A-Fa-f]{2}|[-()_.!~*';/?:@&=+$,A-Za-z0-9])+)" +
            "([).!';/?:,][[:blank:]])?$";

    private static final Pattern URL_PATTERN = Pattern.compile(URL_REGEX);

    private static final String SELECT_QUERY_PARAM_NAME = "SELECT * FROM SERVICE where name = ?";
    private static final String SELECT_QUERY_ALL = "SELECT * FROM SERVICE";
    private Set<Service> services = new HashSet<>();
    private String messages ="";
    private DBConnector connector;
    private BackgroundPoller poller = new BackgroundPoller();

    @Override
    public void start() {
        Promise<Void> dbPromise = Promise.promise();
        vertx.executeBlocking(execute -> {
            logger.info("Executing DB promise");
            connector = new DBConnector(vertx);
            Future<ResultSet> result = connector.query(SELECT_QUERY_ALL);
            result.onComplete(ar -> {
                if (ar.failed()) {
                    logger.error("Error while querying DBConnector ", ar.cause());
                    throw new RuntimeException(ar.cause());
                } else {
                    List<JsonArray> results = ar.result().getResults();
                    logger.info("Fetched services size: " + results.size());
                    results.forEach(rs -> services.add(new Service(rs.getString(0), rs.getString(1), rs.getString(2))));
                }
            });
            execute.complete();
        }, false, result -> {
            dbPromise.complete();
            logger.info("Completed db promise");
        });
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        Promise<Void> routerPromise = Promise.promise();
        vertx.executeBlocking(execute -> {
            logger.info("Executing routerPromise promise");
            vertx.setPeriodic(1000 * 60, timerId -> poller.pollServices(vertx, connector, services));
            setRoutes(router);
        }, false, result -> {
            routerPromise.complete();
            logger.info("Completed routerPromise promise");
        });
        listenHttpRequest(router);
    }

    private void setRoutes(Router router) {
        router.route("/*").handler(StaticHandler.create());
        router.get("/service").handler(req -> {
            List<JsonObject> jsonServices = services
                    .stream()
                    .map(service ->
                            new JsonObject()
                                    .put("name", service.getName())
                                    .put("url", service.getUrl())
                                    .put("status", service.getStatus()))
                    .collect(Collectors.toList());
            req.response()
                    .putHeader("content-type", "application/json")
                    .end(new JsonArray(jsonServices).encode());
        });
        router.post("/service").handler(req -> {
            JsonObject jsonBody = req.getBodyAsJson();
            String url = jsonBody.getString("url");
            if (isValidateService(url)) {
                boolean addedOrUpdated = addOrUpdateService(jsonBody.getString("name"), jsonBody.getString("url"));
                if (addedOrUpdated) {
                    req.response()
                            .putHeader("content-type", "text/plain")
                            .end("OK");
                }
            } else {
                System.out.println("Invalid Service URL  " + url);
                req.response()
                        .putHeader("content-type", "text/plain")
                        .end("FAIL");
            }
        });
        router.delete("/service").handler(req -> {
            JsonObject jsonBody = req.getBodyAsJson();
            boolean isDeleted = services.removeIf(service -> service.getName().toLowerCase().equals(jsonBody.getString("name")));
            if (isDeleted) {
                req.response()
                        .putHeader("content-type", "text/plain")
                        .end("FAIL");
            } else {
                connector.deleteService(jsonBody.getString("name"));
                req.response()
                        .putHeader("content-type", "text/plain")
                        .end("OK");
            }
        });
    }

    private void listenHttpRequest(Router router) {
        vertx
                .createHttpServer()
                .requestHandler(router)
                .listen(8080, service -> {
                    if (service.succeeded()) {
                        logger.info("KRY code test service started");
                    } else {
                        logger.error("KRY code test service failed to start", service.cause());
                    }
                });
    }

    private boolean addOrUpdateService(String name, String url) {
        if (!isServiceExists(name)) {
            connector.insertService(name, url);
        } else {
            services.removeIf(service1 -> service1.getName().equalsIgnoreCase(name));
            connector.updateService(new JsonArray().add(url).add("").add(name));
        }
        return services.add(new Service(name, url, ""));
    }

    private boolean isServiceExists(String serviceName) {
        return services.stream().anyMatch(service -> service.getName().equalsIgnoreCase(serviceName));
    }

    private boolean isValidateService(String url) {
        if (url == null) {
            return false;
        }
        Matcher matcher = URL_PATTERN.matcher(url);
        return matcher.matches();
    }

}



