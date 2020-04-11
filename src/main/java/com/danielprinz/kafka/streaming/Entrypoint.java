package com.danielprinz.kafka.streaming;

import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class Entrypoint extends AbstractVerticle {

  private static final Logger LOG = LogManager.getLogger(Entrypoint.class);

  static final String OUTPUT_TOPIC_EVEN = "even-numbers-output";
  static final String OUTPUT_TOPIC_ODD = "odd-numbers-output";
  static final String INPUT_TOPIC = "number-input";
  private final String bootstrapServers;

  public Entrypoint(String bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
  }

  /**
   * Run with: -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector
   */
  public static void main(String[] args) {
    final VertxOptions options = new VertxOptions()
      .setMaxEventLoopExecuteTime(100)
      .setMaxEventLoopExecuteTimeUnit(TimeUnit.MILLISECONDS)
      .setEventLoopPoolSize(2)
      .setWorkerPoolSize(2);
    final Vertx vertx = Vertx.vertx(options);
    vertx.deployVerticle(new Entrypoint("127.0.0.1:9091"));
  }

  @Override
  public void start(final Promise<Void> start) {
    LOG.info("Starting...");
    vertx.deployVerticle(new KafkaStream(bootstrapServers), ar -> start.handle(ar.mapEmpty()));
  }

}

