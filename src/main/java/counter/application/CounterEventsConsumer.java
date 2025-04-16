package counter.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import counter.domain.CounterEvent;
import counter.domain.CounterEvent.ValueIncreased;
import counter.domain.CounterEvent.ValueMultiplied;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("counter-events-consumer") // <1>
@Consume.FromEventSourcedEntity(CounterEntity.class) // <2>
public class CounterEventsConsumer extends Consumer { // <3>

  private Logger logger = LoggerFactory.getLogger(CounterEventsConsumer.class);

  public Effect onEvent(CounterEvent event) { // <4>
    logger.info("Received increased event: {} (msg ce id {})", event.toString(), messageContext().metadata().asCloudEvent().id());
    return switch (event) {
      case ValueIncreased valueIncreased ->
        //processing value increased event
        effects().done(); // <5>
      case ValueMultiplied valueMultiplied -> effects().ignore(); // <6>
    };
  }
}
