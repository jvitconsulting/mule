/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.processor;

import org.mule.runtime.core.api.event.BaseEvent;
import org.mule.runtime.core.api.event.BaseEventContext;

import java.util.function.Consumer;

/**
 * Used to dispatch {@link BaseEvent}'s asynchronously for processing. The result of asynchronous processing can be obtained
 * by subscribing to the {@link BaseEvent}'s {@link BaseEventContext}.
 * <p/>
 * All Sinks must support concurrent calls from multiple publishers and it is then up to each implementation to determine how to
 * handle this, i.e.
 * <ol>
 * <li>By continuing in the caller thread.</li>
 * <li>Serializing all events to a single thread.</li>
 * <li>Using a ring-buffer to de-multiplex requests and then handle them with 1..n subscribers.</li>
 * </ol>
 *
 * @since 4.0
 */
public interface Sink extends Consumer<BaseEvent> {

  /**
   * Submit the given {@link BaseEvent} for processing without a timeout. If the {@link BaseEvent} cannot be processed
   * immediately due to back-pressure then this method will block until in can be processed.
   *
   * @param event the {@link BaseEvent} to dispatch for processing
   */
  @Override
  void accept(BaseEvent event);

  /**
   * Submit the given {@link BaseEvent} for processing. If the {@link BaseEvent} cannot be processed immediately due to
   * back-pressure then this method will return {@code false}.
   *
   * @param event the {@link BaseEvent} to dispatch for processing
   * @return {@code true} is the {@link BaseEvent} was submitted for processing successfully, {@code false} otherwise.
   */
  boolean emit(BaseEvent event);

}
