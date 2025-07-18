package counter.application;

import static counter.domain.CounterEvent.ValueIncreased;
import static counter.domain.CounterEvent.ValueMultiplied;
import static java.util.function.Function.identity;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import counter.domain.CounterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("counter")
public class CounterEntity extends EventSourcedEntity<Integer, CounterEvent> {

  private Logger logger = LoggerFactory.getLogger(CounterEntity.class);

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME) // <1>
  @JsonSubTypes(
    {
      @JsonSubTypes.Type(value = CounterResult.Success.class, name = "Success"),
      @JsonSubTypes.Type(
        value = CounterResult.ExceedingMaxCounterValue.class,
        name = "ExceedingMaxCounterValue"
      ),
    }
  )
  public sealed interface CounterResult { // <2>
    record ExceedingMaxCounterValue(String message) implements CounterResult {}

    record Success(int value) implements CounterResult {}
  }


  @Override
  public Integer emptyState() {
    return 0;
  }

  public Effect<Integer> increase(Integer value) {
    logger.info("Counter {} increased by {}", this.commandContext().entityId(), value);
    return effects()
      .persist(new ValueIncreased(value, currentState() + value))
      .thenReply(identity());
  }

  public Effect<Integer> increaseWithError(Integer value) {
    if (currentState() + value > 10000) {
      return effects().error("Increasing the counter above 10000 is blocked"); // <1>
    }
    logger.info("Counter {} increased by {}", this.commandContext().entityId(), value);
    return effects()
      .persist(new ValueIncreased(value, currentState() + value))
      .thenReply(identity());
  }


  public Effect<CounterResult> increaseWithResult(Integer value) {
    if (currentState() + value > 10000) {
      return effects()
        .reply(
          new CounterResult.ExceedingMaxCounterValue(
            "Increasing the counter above 10000 is blocked"
          )
        ); // <3>
    }
    logger.info("Counter {} increased by {}", this.commandContext().entityId(), value);
    return effects()
      .persist(new ValueIncreased(value, currentState() + value))
      .thenReply(CounterResult.Success::new); // <4>
  }


  public ReadOnlyEffect<Integer> get() {
    return effects().reply(currentState());
  }

  public Effect<Integer> multiply(Integer value) {
    logger.info("Counter {} multiplied by {}", this.commandContext().entityId(), value);
    return effects()
      .persist(new ValueMultiplied(value, currentState() * value))
      .thenReply(identity());
  }

  @Override
  public Integer applyEvent(CounterEvent event) {
    return switch (event) {
      case ValueIncreased evt -> evt.updatedValue();
      case ValueMultiplied evt -> evt.updatedValue();
    };
  }
}
