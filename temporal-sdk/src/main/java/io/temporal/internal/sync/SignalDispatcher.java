package io.temporal.internal.sync;

import static io.temporal.internal.common.InternalUtils.TEMPORAL_RESERVED_PREFIX;

import io.temporal.api.common.v1.Payloads;
import io.temporal.api.sdk.v1.WorkflowInteractionDefinition;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.converter.DataConverterException;
import io.temporal.common.converter.EncodedValues;
import io.temporal.common.interceptors.Header;
import io.temporal.common.interceptors.WorkflowInboundCallsInterceptor;
import io.temporal.common.interceptors.WorkflowOutboundCallsInterceptor;
import io.temporal.worker.MetricsType;
import io.temporal.workflow.DynamicSignalHandler;
import io.temporal.workflow.HandlerUnfinishedPolicy;
import io.temporal.workflow.Workflow;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SignalDispatcher {
  private static final Logger log = LoggerFactory.getLogger(SignalDispatcher.class);

  private final DataConverter dataConverterWithWorkflowContext;
  private final Map<String, WorkflowOutboundCallsInterceptor.SignalRegistrationRequest>
      signalCallbacks = new HashMap<>();

  private WorkflowInboundCallsInterceptor inboundCallsInterceptor;
  private DynamicSignalHandler dynamicSignalHandler;

  /** Buffers signals which don't have a registered listener. */
  private final Queue<SignalData> signalBuffer = new ArrayDeque<>();

  private Map<Long, SignalHandlerInfo> runningSignalHandlers = new LinkedHashMap<>();

  public SignalDispatcher(DataConverter dataConverterWithWorkflowContext) {
    this.dataConverterWithWorkflowContext = dataConverterWithWorkflowContext;
  }

  public void setInboundCallsInterceptor(WorkflowInboundCallsInterceptor inboundCallsInterceptor) {
    this.inboundCallsInterceptor = inboundCallsInterceptor;
  }

  /** Called from the interceptor tail */
  public void handleInterceptedSignal(WorkflowInboundCallsInterceptor.SignalInput input) {
    String signalName = input.getSignalName();
    Object[] args = input.getArguments();
    WorkflowOutboundCallsInterceptor.SignalRegistrationRequest handler =
        signalCallbacks.get(signalName);
    if (handler == null) {
      if (dynamicSignalHandler != null) {
        dynamicSignalHandler.handle(signalName, (EncodedValues) args[0]);
        return;
      }
      throw new IllegalStateException("Unknown signal type: " + signalName);
    } else {
      handler.getCallback().apply(args);
    }
  }

  public Map<Long, SignalHandlerInfo> getRunningSignalHandlers() {
    return runningSignalHandlers;
  }

  public void handleSignal(
      String signalName, Optional<Payloads> input, long eventId, Header header) {
    WorkflowOutboundCallsInterceptor.SignalRegistrationRequest handler =
        signalCallbacks.get(signalName);
    Object[] args;
    HandlerUnfinishedPolicy policy;
    if (signalName.startsWith(TEMPORAL_RESERVED_PREFIX)) {
      // Ignore internal signals
      return;
    }
    if (handler == null) {
      if (dynamicSignalHandler == null) {
        signalBuffer.add(new SignalData(signalName, input, eventId, header));
        return;
      }
      args = new Object[] {new EncodedValues(input, dataConverterWithWorkflowContext)};
      policy = dynamicSignalHandler.getUnfinishedPolicy(signalName);
    } else {
      try {
        args =
            dataConverterWithWorkflowContext.fromPayloads(
                input, handler.getArgTypes(), handler.getGenericArgTypes());
      } catch (DataConverterException e) {
        logSerializationException(signalName, eventId, e);
        return;
      }
      policy = handler.getUnfinishedPolicy();
    }
    // Track the signal handler
    boolean threadDestroyed = false;
    runningSignalHandlers.put(eventId, new SignalHandlerInfo(eventId, signalName, policy));
    try {
      inboundCallsInterceptor.handleSignal(
          new WorkflowInboundCallsInterceptor.SignalInput(signalName, args, eventId, header));
    } catch (DestroyWorkflowThreadError e) {
      threadDestroyed = true;
      throw e;
    } finally {
      // If the thread was destroyed the user did not finish the handler
      if (!threadDestroyed) {
        runningSignalHandlers.remove(eventId);
      }
    }
  }

  public void registerSignalHandlers(
      WorkflowOutboundCallsInterceptor.RegisterSignalHandlersInput input) {
    for (WorkflowOutboundCallsInterceptor.SignalRegistrationRequest request : input.getRequests()) {
      String signalType = request.getSignalType();
      if (signalCallbacks.containsKey(signalType)) {
        throw new IllegalStateException("Signal \"" + signalType + "\" is already registered");
      }
      signalCallbacks.put(signalType, request);
    }
    for (SignalData signalData : signalBuffer) {
      handleSignal(
          signalData.getSignalName(),
          signalData.getPayload(),
          signalData.getEventId(),
          signalData.getHeader());
    }
  }

  public void registerDynamicSignalHandler(
      WorkflowOutboundCallsInterceptor.RegisterDynamicSignalHandlerInput input) {
    dynamicSignalHandler = input.getHandler();
    for (SignalData signalData : signalBuffer) {
      dynamicSignalHandler.handle(
          signalData.getSignalName(),
          new EncodedValues(signalData.getPayload(), dataConverterWithWorkflowContext));
    }
  }

  private void logSerializationException(
      String signalName, Long eventId, DataConverterException exception) {
    log.error(
        "Failure deserializing signal input for \""
            + signalName
            + "\" at eventId "
            + eventId
            + ". Dropping it.",
        exception);
    Workflow.getMetricsScope().counter(MetricsType.CORRUPTED_SIGNALS_COUNTER).inc(1);
  }

  public List<WorkflowInteractionDefinition> getSignalHandlers() {
    List<WorkflowInteractionDefinition> handlers = new ArrayList<>(signalCallbacks.size() + 1);
    for (Map.Entry<String, WorkflowOutboundCallsInterceptor.SignalRegistrationRequest> entry :
        signalCallbacks.entrySet()) {
      WorkflowOutboundCallsInterceptor.SignalRegistrationRequest handler = entry.getValue();
      handlers.add(
          WorkflowInteractionDefinition.newBuilder()
              .setName(handler.getSignalType())
              .setDescription(handler.getDescription())
              .build());
    }
    if (dynamicSignalHandler != null) {
      handlers.add(
          WorkflowInteractionDefinition.newBuilder()
              .setDescription(dynamicSignalHandler.getDescription())
              .build());
    }
    handlers.sort(Comparator.comparing(WorkflowInteractionDefinition::getName));
    return handlers;
  }

  private static class SignalData {
    private final String signalName;
    private final Optional<Payloads> payload;
    private final long eventId;
    private final Header header;

    private SignalData(String signalName, Optional<Payloads> payload, long eventId, Header header) {
      this.signalName = Objects.requireNonNull(signalName);
      this.payload = Objects.requireNonNull(payload);
      this.eventId = eventId;
      this.header = header;
    }

    public String getSignalName() {
      return signalName;
    }

    public Optional<Payloads> getPayload() {
      return payload;
    }

    public long getEventId() {
      return eventId;
    }

    public Header getHeader() {
      return header;
    }
  }
}
