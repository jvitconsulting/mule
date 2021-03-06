/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.routing;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mule.runtime.api.message.Message.of;
import static org.mule.runtime.core.api.transaction.TransactionCoordination.getInstance;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.core.api.event.BaseEvent;
import org.mule.runtime.core.api.exception.MessagingException;
import org.mule.runtime.core.privileged.processor.InternalProcessor;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.api.retry.policy.RetryPolicyExhaustedException;
import org.mule.runtime.core.api.transaction.Transaction;
import org.mule.tck.junit4.AbstractMuleContextTestCase;

import java.io.ByteArrayInputStream;
import java.util.Collection;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UntilSuccessfulTestCase extends AbstractMuleContextTestCase {

  public static class ConfigurableMessageProcessor implements Processor, InternalProcessor {

    private volatile int eventCount;
    private volatile BaseEvent event;
    private volatile int numberOfFailuresToSimulate;

    @Override
    public BaseEvent process(final BaseEvent evt) throws MuleException {
      eventCount++;
      if (numberOfFailuresToSimulate-- > 0) {
        throw new RuntimeException("simulated problem");
      }
      this.event = evt;
      return evt;
    }

    public BaseEvent getEventReceived() {
      return event;
    }

    public int getEventCount() {
      return eventCount;
    }

    public void setNumberOfFailuresToSimulate(int numberOfFailuresToSimulate) {
      this.numberOfFailuresToSimulate = numberOfFailuresToSimulate;
    }
  }

  @Parameters
  public static Collection<Boolean> modeParameters() {
    return asList(new Boolean[] {Boolean.TRUE, Boolean.FALSE});
  }

  @Rule
  public ExpectedException expected = ExpectedException.none();
  private UntilSuccessful untilSuccessful;
  private ConfigurableMessageProcessor targetMessageProcessor;
  private boolean tx;

  public UntilSuccessfulTestCase(boolean tx) {
    this.tx = tx;
  }

  @Override
  protected void doSetUp() throws Exception {
    super.doSetUp();
    untilSuccessful = buildUntilSuccessful(1000L);
    if (tx) {
      getInstance().bindTransaction(mock(Transaction.class));
    }

  }

  @After
  public void doTeardown() throws Exception {
    untilSuccessful.dispose();
    super.doTearDown();
  }

  private UntilSuccessful buildUntilSuccessful(Long millisBetweenRetries) throws Exception {
    UntilSuccessful untilSuccessful = new UntilSuccessful();
    untilSuccessful.setMaxRetries(2);
    untilSuccessful.setAnnotations(getAppleFlowComponentLocationAnnotations());
    if (millisBetweenRetries != null) {
      untilSuccessful.setMillisBetweenRetries(millisBetweenRetries);
    }

    targetMessageProcessor = new ConfigurableMessageProcessor();
    untilSuccessful.setMessageProcessors(singletonList(targetMessageProcessor));
    muleContext.getInjector().inject(untilSuccessful);
    return untilSuccessful;
  }

  @Override
  protected void doTearDown() throws Exception {
    untilSuccessful.stop();
  }

  @Test
  public void testSuccessfulDelivery() throws Exception {
    untilSuccessful.initialise();
    untilSuccessful.start();

    assertLogicallyEqualEvents(testEvent(), untilSuccessful.process(testEvent()));
    assertTargetEventReceived(testEvent());
  }

  @Test
  public void testSuccessfulDeliveryStreamPayload() throws Exception {
    untilSuccessful.setMuleContext(muleContext);
    untilSuccessful.initialise();
    untilSuccessful.start();

    final BaseEvent testEvent =
        eventBuilder().message(of(new ByteArrayInputStream("test_data".getBytes()))).build();
    assertSame(testEvent.getMessage(), untilSuccessful.process(testEvent).getMessage());
    assertTargetEventReceived(testEvent);
  }

  @Test
  public void testPermanentDeliveryFailure() throws Exception {
    targetMessageProcessor.setNumberOfFailuresToSimulate(Integer.MAX_VALUE);
    untilSuccessful.setMuleContext(muleContext);
    untilSuccessful.initialise();
    untilSuccessful.start();

    final BaseEvent testEvent = eventBuilder().message(of("ERROR")).build();
    expected.expect(MessagingException.class);
    expected.expectCause(instanceOf(RetryPolicyExhaustedException.class));
    try {
      untilSuccessful.process(testEvent);
    } finally {
      assertEquals(1 + untilSuccessful.getMaxRetries(), targetMessageProcessor.getEventCount());
    }
  }

  @Test
  public void testTemporaryDeliveryFailure() throws Exception {
    targetMessageProcessor.setNumberOfFailuresToSimulate(untilSuccessful.getMaxRetries());
    untilSuccessful.setMuleContext(muleContext);
    untilSuccessful.initialise();
    untilSuccessful.start();

    final BaseEvent testEvent = eventBuilder().message(of("ERROR")).build();
    assertSame(testEvent.getMessage(), untilSuccessful.process(testEvent).getMessage());
    assertTargetEventReceived(testEvent);
    assertEquals(targetMessageProcessor.getEventCount(), untilSuccessful.getMaxRetries() + 1);
  }

  @Test
  public void testDefaultMillisWait() throws Exception {
    untilSuccessful = buildUntilSuccessful(null);
    untilSuccessful.initialise();
    untilSuccessful.start();
    assertEquals(60 * 1000, untilSuccessful.getMillisBetweenRetries());
  }

  private void assertTargetEventReceived(BaseEvent request) throws MuleException {
    assertThat(targetMessageProcessor.getEventReceived(), not(nullValue()));
    assertLogicallyEqualEvents(request, targetMessageProcessor.getEventReceived());
  }

  private void assertLogicallyEqualEvents(final BaseEvent testEvent, BaseEvent eventReceived) throws MuleException {
    // events have been rewritten so are different but the correlation ID has been carried around
    assertEquals(testEvent.getCorrelationId(), eventReceived.getCorrelationId());
    // and their payload
    assertEquals(testEvent.getMessage(), eventReceived.getMessage());
  }



}
