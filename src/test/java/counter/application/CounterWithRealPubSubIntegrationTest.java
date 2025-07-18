package counter.application;

import static org.assertj.core.api.Assertions.assertThat;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class CounterWithRealPubSubIntegrationTest extends TestKitSupport { // <1>

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withEventingSupport(
      TestKit.Settings.EventingSupport.GOOGLE_PUBSUB
    );
  }


  @Test
  public void verifyCounterEventSourcedConsumesFromPubSub() {
    // using random id to ensure isolation when running tests locally
    // with a pubsub container since the container preserves state
    var counterId = UUID.randomUUID().toString();

    var msg =
      """
        { "counterId": "%s", "value":20 }
      """.formatted(counterId);

    var messageBody = buildMessageBody(
      msg,
      CounterCommandFromTopicConsumer.IncreaseCounter.class.getName()
    );

    var pubSubClient = testKit.getHttpClientProvider().httpClientFor("http://localhost:8085");

    // Make sure we wait for the topic to be created by the runtime
    Awaitility.await()
      .ignoreExceptions()
      .atMost(Duration.ofSeconds(15))
      .untilAsserted(() -> {
        var result = pubSubClient.GET("/v1/projects/test/topics/counter-commands").invoke();
        assertThat(result.httpResponse().status())
          .as("Topic counter-command exists in google pubsub broker")
          .isEqualTo(StatusCodes.OK);
      });

    // publish an event
    var response = pubSubClient
      .POST("/v1/projects/test/topics/counter-commands:publish")
      .withRequestBody(
        ContentTypes.APPLICATION_JSON,
        messageBody.getBytes(StandardCharsets.UTF_8)
      )
      .invoke();

    assertThat(response.httpResponse().status()).isEqualTo(StatusCodes.OK);

    Awaitility.await()
      .ignoreExceptions()
      .atMost(30, TimeUnit.SECONDS)
      .untilAsserted(
        () ->
          assertThat(
            componentClient
              .forEventSourcedEntity(counterId)
              .method(CounterEntity::get)
              .invoke()
          ).isEqualTo(20)
      );
  }

  // builds a message in PubSub format, ready to be injected
  private String buildMessageBody(String jsonMsg, String ceType) {
    var data = Base64.getEncoder().encodeToString(jsonMsg.getBytes());

    return """
    {
        "messages": [
            {
                "data": "%s",
                "attributes": {
                    "Content-Type": "application/json",
                    "ce-specversion": "1.0",
                    "ce-type": "%s"
                }
            }
        ]
    }
    """.formatted(data, ceType);
  }
}
