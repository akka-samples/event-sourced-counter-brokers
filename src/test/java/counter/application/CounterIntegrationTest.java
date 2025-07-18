package counter.application;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import akka.javasdk.CloudEvent;
import akka.javasdk.testkit.EventingTestKit;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import counter.application.CounterCommandFromTopicConsumer.IncreaseCounter;
import counter.application.CounterCommandFromTopicConsumer.MultiplyCounter;
import counter.domain.CounterEvent.ValueIncreased;
import counter.domain.CounterEvent.ValueMultiplied;
import java.net.URI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CounterIntegrationTest extends TestKitSupport { // <1>


  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withTopicIncomingMessages("counter-commands") // <1>
      .withTopicOutgoingMessages("counter-events") // <2>
      .withTopicOutgoingMessages("counter-events-with-meta");
  }


  private EventingTestKit.IncomingMessages commandsTopic;
  private EventingTestKit.OutgoingMessages eventsTopic;

  private EventingTestKit.OutgoingMessages eventsTopicWithMeta;


  @BeforeAll
  public void beforeAll() {
    super.beforeAll();
    commandsTopic = testKit.getTopicIncomingMessages("counter-commands"); // <2>
    eventsTopic = testKit.getTopicOutgoingMessages("counter-events");

    eventsTopicWithMeta = testKit.getTopicOutgoingMessages("counter-events-with-meta");
  }


  // since multiple tests are using the same topics, make sure to reset them before each new test
  // so unread messages from previous tests do not mess with the current one
  @BeforeEach // <1>
  public void clearTopics() {
    eventsTopic.clear(); // <2>
    eventsTopicWithMeta.clear();
  }


  @Test
  public void verifyCounterEventSourcedWiring() {
    var counterClient = componentClient.forEventSourcedEntity("001");

    // increase counter (from 0 to 10)
    counterClient.method(CounterEntity::increase).invoke(10);

    // multiply by 20 (from 10 to 200)
    counterClient.method(CounterEntity::multiply).invoke(20);

    var result = counterClient.method(CounterEntity::get).invoke();

    assertThat(result).isEqualTo(200);
  }


  @Test
  public void verifyCounterEventSourcedPublishToTopic() {
    var counterId = "test-topic";
    var increaseCmd = new IncreaseCounter(counterId, 3);
    var multipleCmd = new MultiplyCounter(counterId, 4);

    commandsTopic.publish(increaseCmd, counterId); // <3>
    commandsTopic.publish(multipleCmd, counterId);

    var eventIncreased = eventsTopic.expectOneTyped(ValueIncreased.class, ofSeconds(20)); // <4>
    var eventMultiplied = eventsTopic.expectOneTyped(ValueMultiplied.class);

    assertEquals(increaseCmd.value(), eventIncreased.getPayload().value()); // <5>
    assertEquals(multipleCmd.value(), eventMultiplied.getPayload().multiplier());
  }


  @Test
  public void verifyIgnoreUnknownToTopic() {
    var counterId = "test-ignore";
    var ignoreCmd = new CounterCommandFromTopicConsumer.IgnoredEvent("test");
    var increaseCmd = new IncreaseCounter(counterId, 1);

    commandsTopic.publish(ignoreCmd, counterId);
    commandsTopic.publish(increaseCmd, counterId);

    var eventIncreased = eventsTopic.expectOneTyped(ValueIncreased.class, ofSeconds(20)); // <5>

    assertEquals(increaseCmd.value(), eventIncreased.getPayload().value()); // <6>
  }

  @Test
  public void verifyCounterCommandsAndPublishWithMetadata() {
    var counterId = "test-topic-metadata";
    var increaseCmd = new IncreaseCounter(counterId, 10);

    var metadata = CloudEvent.of( // <1>
      "cmd1",
      URI.create("CounterTopicIntegrationTest"),
      increaseCmd.getClass().getName()
    )
      .withSubject(counterId) // <2>
      .asMetadata()
      .add("Content-Type", "application/json"); // <3>

    commandsTopic.publish(testKit.getMessageBuilder().of(increaseCmd, metadata)); // <4>

    var increasedEvent = eventsTopicWithMeta.expectOneTyped(IncreaseCounter.class);
    var actualMd = increasedEvent.getMetadata(); // <5>
    assertEquals(counterId, actualMd.asCloudEvent().subject().get()); // <6>
    assertEquals("application/json", actualMd.get("Content-Type").get());
  }
}
