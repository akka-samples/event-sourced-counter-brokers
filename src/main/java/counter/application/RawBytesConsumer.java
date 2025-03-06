package counter.application;

import akka.javasdk.consumer.Consumer;

public class RawBytesConsumer extends Consumer {

  public Effect onMessage(byte[] bytes) { // <1>
    // deserialization logic here
    return effects().done();
  }
}
