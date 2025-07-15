package counter.domain;

import akka.javasdk.annotations.TypeName;

public sealed interface CounterEvent {
  @TypeName("valie-increased")
  record ValueIncreased(int value, int updatedValue) implements CounterEvent {}

  @TypeName("value-multiplied")
  record ValueMultiplied(int multiplier, int updatedValue) // <1>
    implements CounterEvent {}
}
