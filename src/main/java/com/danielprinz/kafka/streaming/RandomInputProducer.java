package com.danielprinz.kafka.streaming;

import static java.time.ZoneOffset.UTC;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.kafka.client.serialization.JsonObjectSerializer;
import lombok.Builder;
import lombok.Value;

public class RandomInputProducer extends AbstractVerticle {

  private final static Logger LOG = LogManager.getLogger(Entrypoint.class);
  private static final Random r = new Random();

  private final AtomicInteger producedRecords = new AtomicInteger();
  private final Settings settings;
  private long timerId = 0;

  public RandomInputProducer(Settings settings) {
    this.settings = settings;
  }

  /**
   * Run with: -Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector
   */
  public static void main(String[] args) {
    final Vertx vertx = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(1).setWorkerPoolSize(1));
    final Settings settings = Settings.builder()
      .bootstrapServers("127.0.0.1:9091")
      .backoffTimeMs(100)
      .batchSize(100)
      .maxRecordsToProduce(Integer.MAX_VALUE)
      .build();
    vertx.deployVerticle(new RandomInputProducer(settings));
  }

  @Override
  public void start(final Promise<Void> startPromise) {
    final KafkaProducer<Void, JsonObject> producer = KafkaProducer.create(vertx, producerOptions());
    ((ContextInternal) vertx.getOrCreateContext()).addCloseHook(producer::close);
    producer.exceptionHandler(LOG::error);

    timerId = vertx.setPeriodic(settings.getBackoffTimeMs(), ignored -> {
      final AtomicInteger count = new AtomicInteger();
      final long ref = System.currentTimeMillis();
      for (long l = 0; l <= settings.getBatchSize(); l++) {
        final int currentProduced = producedRecords.getAndIncrement();
        if (currentProduced >= settings.getMaxRecordsToProduce()) {
          // Stopped producing...
          LOG.trace("Stopped producing records as max record size is reached.");
        }
        final ZonedDateTime now = ZonedDateTime.now(UTC);
        final KafkaProducerRecord<Void, JsonObject> toProduce = KafkaProducerRecord.create(Entrypoint.INPUT_TOPIC,
          JsonObject.mapFrom(new NumberEvent(l, now.toEpochSecond(), generateRandomData())));
        producer.send(toProduce, ar -> {
          LOG.trace("Produced Offset {} in {} ms", ar.result().getOffset(), time(ar.result().getTimestamp()));
          if (settings.getBatchSize() == count.getAndIncrement()) {
            LOG.info("Produced batch in {} ms. Records produced {}.", time(ref), currentProduced);
          }
        });
      }
    });
  }

  private String generateRandomData() {
    char[] chars = new char[512];
    Arrays.fill(chars, (char) (r.nextInt(26) + 'a'));
    return new String(chars);
  }

  private long time(final long referenceTimestamp) {
    return System.currentTimeMillis() - referenceTimestamp;
  }

  private HashMap<String, String> producerOptions() {
    final HashMap<String, String> options = new HashMap<>();
    options.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, settings.getBootstrapServers());
    options.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    options.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonObjectSerializer.class.getName());
    options.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
    options.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5"); // max supported
    options.put(ProducerConfig.ACKS_CONFIG, "all");
    return options;
  }

  @Builder
  @Value
  static class Settings {
    String bootstrapServers;
    int batchSize;
    long backoffTimeMs;
    int maxRecordsToProduce;
  }

}
