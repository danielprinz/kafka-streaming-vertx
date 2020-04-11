package com.danielprinz.kafka.streaming;

import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.salesforce.kafka.test.junit5.SharedKafkaTestResource;

import io.vertx.core.Vertx;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
public class EntrypointTest {

  private static final Logger LOG = LogManager.getLogger(EntrypointTest.class);

  @RegisterExtension
  public static final SharedKafkaTestResource KAFKA = new SharedKafkaTestResource().withBrokers(3);
  public static final int MAX_RECORDS = 5000;

  @Timeout(value = 120, timeUnit = TimeUnit.SECONDS)
  @Test
  void shouldConsumeAllEntries(Vertx vertx, VertxTestContext ctx) throws Throwable {
    KAFKA.getKafkaTestUtils().createTopic(Entrypoint.INPUT_TOPIC, 3, (short) 3);
    KAFKA.getKafkaTestUtils().createTopic(Entrypoint.OUTPUT_TOPIC_EVEN, 3, (short) 3);
    KAFKA.getKafkaTestUtils().createTopic(Entrypoint.OUTPUT_TOPIC_ODD, 3, (short) 3);

    vertx.eventBus().consumer("done", msg -> {
      LOG.info("Done...");
      ctx.completeNow();
    });

    vertx.deployVerticle(new TestConsumer(KAFKA.getKafkaConnectString()), ar -> {
      failOnErrorOrContinue(ctx, ar.failed(), ar.cause());
      vertx.deployVerticle(new Entrypoint(KAFKA.getKafkaConnectString()), deployed -> {
        failOnErrorOrContinue(ctx, deployed.failed(), deployed.cause());
        produceInput(vertx, ctx);
      });
    });
  }

  private void failOnErrorOrContinue(final VertxTestContext ctx, final boolean failed, final Throwable cause) {
    if (failed) {
      ctx.failNow(cause);
    }
  }

  private void produceInput(final Vertx vertx, final VertxTestContext ctx) {
    vertx.deployVerticle(new RandomInputProducer(RandomInputProducer.Settings.builder()
      .bootstrapServers(KAFKA.getKafkaConnectString())
      .backoffTimeMs(1000)
      .batchSize(500)
      .maxRecordsToProduce(MAX_RECORDS)
      .build()), ctx.succeeding());
  }

}
