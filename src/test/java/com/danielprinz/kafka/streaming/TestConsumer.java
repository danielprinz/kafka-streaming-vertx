package com.danielprinz.kafka.streaming;

import static com.danielprinz.kafka.streaming.EntrypointTest.MAX_RECORDS;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.serialization.JsonObjectDeserializer;

public class TestConsumer extends AbstractVerticle {

  private static final Logger LOG = LogManager.getLogger(EntrypointTest.class);
  private final String bootstrapServers;

  private final AtomicInteger count = new AtomicInteger();

  TestConsumer(String bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
  }

  @Override
  public void start(final Promise<Void> start) throws Exception {
    final Promise<Void> one = Promise.promise();
    final Promise<Void> two = Promise.promise();
    createConsumer(Entrypoint.OUTPUT_TOPIC_EVEN, one);
    createConsumer(Entrypoint.OUTPUT_TOPIC_ODD, two);
    CompositeFuture.all(one.future(), two.future()).onComplete(ar ->
      start.handle(ar.mapEmpty())
    );
  }

  private void createConsumer(final String topic, final Promise<Void> start) {
    KafkaConsumer.<Void, JsonObject>create(vertx, testConsumerOptions())
      .exceptionHandler(LOG::error)
      .handler(event -> {
        LOG.info("Found event #{} on offset {} from topic {}", count.get(), event.offset(), event.topic());
        if (count.incrementAndGet() >= MAX_RECORDS) {
          vertx.eventBus().publish("done", "done");
        }
      }).subscribe(topic, start);
  }

  private HashMap<String, String> testConsumerOptions() {
    final HashMap<String, String> options = new HashMap<>();
    options.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    options.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    options.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonObjectDeserializer.class.getName());
    options.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    options.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer");
    options.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
    return options;
  }

}
