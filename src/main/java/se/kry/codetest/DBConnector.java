package se.kry.codetest;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;

public class DBConnector {

    private Logger logger = LoggerFactory.getLogger(DBConnector.class);
    private final String DB_PATH = "poller.db";
    private final SQLClient client;

    public DBConnector(Vertx vertx) {
        JsonObject config = new JsonObject()
                .put("url", "jdbc:sqlite:" + DB_PATH)
                .put("driver_class", "org.sqlite.JDBC")
                .put("max_pool_size", 30);

        client = JDBCClient.createShared(vertx, config);
        client.getConnection(conn -> {
            if (conn.failed()) {
                System.err.println(conn.cause().getMessage());
                return;
            }
            final SQLConnection connection = conn.result();
            //executeQuery(connection, "DROP TABLE service", new JsonArray());
            executeQuery(connection, "CREATE TABLE IF NOT EXISTS service (name VARCHAR(128) NOT NULL, url VARCHAR(128) NOT NULL, status VARCHAR(8) NULL)", new JsonArray());
            Future<ResultSet> result = query("select * from service");
            result.onComplete(ar -> {
                if (ar.failed()) {
                    logger.error("Error occurred while loading initial data", ar.cause());
                    throw new RuntimeException(ar.cause());
                } else {
                    ResultSet resultSet = result.result();
                    if (resultSet.getNumRows() == 0) {
                        JsonArray params = new JsonArray().add("KRY").add("https://www.kry.se").add("");
                        executeQuery(connection, "insert into service values(?, ?, ?)", params);
                    }
                }
            });
        });
    }

    public void insertService(String name, String url) {
        client.getConnection(conn -> {
            if (conn.failed()) {
                System.err.println(conn.cause().getMessage());
                return;
            }
            final SQLConnection connection = conn.result();
            JsonArray params = new JsonArray().add(name).add(url).add("");
            Future<ResultSet> resultSetFuture = executeQuery(connection, "insert into service values(?, ?, ?)", params);
            resultSetFuture.onFailure(ar -> {
                logger.error("Error occurred while inserting a record", ar.getCause());
                throw new RuntimeException(ar.getCause());
            });
            resultSetFuture.onSuccess(rs -> {
                logger.info("An record has been inserted in DB " + name);
            });
        });
    }

    public void updateService(JsonArray params) {
        client.getConnection(conn -> {
            if (conn.failed()) {
                System.err.println(conn.cause().getMessage());
                return;
            }
            final SQLConnection connection = conn.result();
            Future<ResultSet> resultSetFuture = executeQuery(connection, "update service set url = ?, status= ? where name= ?", params);
            resultSetFuture.onFailure(ar -> {
                logger.error("Something bad happened while updating status for service "+ params.getString(2));
                throw new RuntimeException(ar.getCause());
            });
            resultSetFuture.onSuccess(rs -> logger.info("Service has been updated for " + params.getString(2)));
        });
    }

    public void deleteService(String name) {
        client.getConnection(conn -> {
            if (conn.failed()) {
                System.err.println(conn.cause().getMessage());
                return;
            }
            final SQLConnection connection = conn.result();
            Future<ResultSet> resultSetFuture = executeQuery(connection, "delete from service where name= ?", new JsonArray().add(name));
            resultSetFuture.onFailure(ar -> {
                logger.error("Something bad happened while deleting service "+ name);
                throw new RuntimeException(ar.getCause());
            });
            resultSetFuture.onSuccess(rs -> {
                logger.info(name + " service has been deleted");
            });
        });
    }

    public Future<ResultSet> query(String query) {
        return query(query, new JsonArray());
    }

    private synchronized Future<ResultSet> executeQuery(SQLConnection connection, String query, JsonArray params) {
        Promise<ResultSet> queryResultFuture = Promise.promise();
        connection.queryWithParams(query, params, result -> {
            if (result.failed()) {
                logger.error("Something bad happened while executing query", result.cause());
                throw new RuntimeException(result.cause());
            } else {
                queryResultFuture.complete(result.result());
            }
            connection.close(done -> {
                if (done.failed()) {
                    logger.error("error while closing the SQL connection");
                    throw new RuntimeException(done.cause());
                }
            });
        });
        return queryResultFuture.future();
    }

    Future<ResultSet> query(String query, JsonArray params) {
        if (query == null || query.isEmpty()) {
            return Future.failedFuture("Query is null or empty");
        }
        if (!query.endsWith(";")) {
            query = query + ";";
        }
        Promise<ResultSet> queryResultFuture = Promise.promise();
        String finalQuery = query;
        client.getConnection(conn -> {
            if (conn.failed()) {
                queryResultFuture.fail(conn.cause().getMessage());
            }
            final SQLConnection connection = conn.result();

            connection.queryWithParams(finalQuery, params, result -> {
                if (result.failed()) {
                    queryResultFuture.fail("Cannot retrieve the data from the database");
                } else {
                    queryResultFuture.complete(result.result());
                }
            });
            // and close the connection
            connection.close(done -> {
                if (done.failed()) {
                    throw new RuntimeException(done.cause());
                }
            });
        });
        return queryResultFuture.future();
    }

}
