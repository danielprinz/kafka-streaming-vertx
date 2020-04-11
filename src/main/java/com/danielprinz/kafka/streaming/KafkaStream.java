package com.danielprinz.kafka.streaming;

import static com.danielprinz.kafka.streaming.Entrypoint.*;
import static java.time.ZoneOffset.UTC;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.kafka.client.consumer.KafkaConsumer;
import io.vertx.kafka.client.consumer.KafkaConsumerRecords;
import io.vertx.kafka.client.producer.KafkaProducer;
import io.vertx.kafka.client.producer.KafkaProducerRecord;
import io.vertx.kafka.client.serialization.JsonObjectDeserializer;
import io.vertx.kafka.client.serialization.JsonObjectSerializer;

public class KafkaStream extends AbstractVerticle {

  private static final Logger LOG = LogManager.getLogger(KafkaStream.class);

  private final String bootstrapServers;
  private KafkaConsumer<Void, JsonObject> consumer;
  private KafkaProducer<Void, JsonObject> producer;
  private ZonedDateTime timing = now();

  public KafkaStream(String bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
  }

  @Override
  public void start(final Promise<Void> start) {
    initProducer();
    initConsumer();

    this.consumer.subscribe(Collections.singleton(INPUT_TOPIC), onSubscribe -> {
      if (onSubscribe.failed()) {
        start.fail(onSubscribe.cause());
        return;
      }
      LOG.info("Started {}", getClass().getName());
      start.complete();
      requestBatch();
    });
  }

  private void initConsumer() {
    final HashMap<String, String> options = consumerOptions();
    this.consumer = KafkaConsumer.create(vertx, options);
    this.vertx.getOrCreateContext().addCloseHook(consumer::close);
    this.consumer.exceptionHandler(System.err::println);
  }

  private void initProducer() {
    this.producer = KafkaProducer.create(vertx, producerOptions());
    this.vertx.getOrCreateContext().addCloseHook(producer::close);
    this.producer.exceptionHandler(System.err::println);
    this.producer.unwrap().initTransactions();
  }

  private void onBatch(final KafkaConsumerRecords<Void, JsonObject> records) {
    if (records.isEmpty()) {
      LOG.debug("No entries. Request next batch.");
      requestBatch();
      return;
    }
    final int entrySize = records.size();
    LOG.trace("Consumed batch with {} entries.", entrySize);
    final ZonedDateTime produceStart = now();
    this.producer.unwrap().beginTransaction();
    LOG.trace("{}ms for begin Transaction", ChronoUnit.MILLIS.between(produceStart, now()));
    final List<Future> batchProduced = new ArrayList<>();
    records.records().forEach(record -> {
      final NumberEvent event = record.value().mapTo(NumberEvent.class);
      batchProduced.add(produceTo(event, decideTopic(event)).future());
    });
    CompositeFuture.all(batchProduced).onComplete(ar -> {
      if (ar.failed()){
        LOG.error("Failed to produce records", ar.cause());
        vertx.close();
        return;
      }
      LOG.trace("{}ms for Batch producing", ChronoUnit.MILLIS.between(produceStart, now()));
      final ZonedDateTime commitStart = now();
      this.producer.unwrap().commitTransaction();
      LOG.trace("{}ms for commit Transaction", ChronoUnit.MILLIS.between(commitStart, now()));
      final ZonedDateTime consumerCommitStart = now();
      this.consumer.commit(whenCommitted -> {
        LOG.trace("{}ms for commit offset", ChronoUnit.MILLIS.between(consumerCommitStart, now()));
        LOG.debug("{}ms for Full round with {} entries", ChronoUnit.MILLIS.between(timing, now()), entrySize);
        requestBatch();
      });
    });
  }

  private String decideTopic(final NumberEvent event) {
    if (event.getValue() % 2 == 0) {
      return OUTPUT_TOPIC_EVEN;
    }
    return OUTPUT_TOPIC_ODD;
  }

  private Promise<Void> produceTo(final NumberEvent event, final String outputTopicEven) {
    final Promise<Void> whenProduced = Promise.promise();
    producer.send(KafkaProducerRecord.create(outputTopicEven, JsonObject.mapFrom(event)), ar -> {
      if (ar.failed()) {
        LOG.error("failed to produce record:", ar.cause());
        whenProduced.fail(ar.cause());
        return;
      }
      LOG.trace("Produced offset {}", ar.result().getOffset());
      whenProduced.tryComplete();
    });
    return whenProduced;
  }

  private void requestBatch() {
    timing = now();
    consumer.poll(5000, records -> {
      LOG.trace("{} ms for fetching records ", ChronoUnit.MILLIS.between(timing, now()));
      onBatch(records.result());
    });
  }

  private HashMap<String, String> consumerOptions() {
    final HashMap<String, String> options = new HashMap<>();
    options.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    options.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    options.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonObjectDeserializer.class.getName());
    options.put(ConsumerConfig.GROUP_ID_CONFIG, "input-group");
    options.put(ConsumerConfig.CLIENT_ID_CONFIG, "input-client");
    options.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    options.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    options.put(ConsumerConfig.RECEIVE_BUFFER_CONFIG, "524288"); // 16 x Default 32768
    // wait a max of 200ms to fill up min bytes config
    options.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, "200");
    options.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "2097152");
    options.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500"); // default
    return options;
  }

  private HashMap<String, String> producerOptions() {
    final HashMap<String, String> options = new HashMap<>();
    options.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    options.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    options.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonObjectSerializer.class.getName());
    options.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
    options.put(ProducerConfig.ACKS_CONFIG, "all");
    options.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5"); // max supported
    options.put(ProducerConfig.LINGER_MS_CONFIG, "50");
    options.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "tx-number-producer");
    return options;
  }

  private ZonedDateTime now() {
    return ZonedDateTime.now(UTC);
  }

}

