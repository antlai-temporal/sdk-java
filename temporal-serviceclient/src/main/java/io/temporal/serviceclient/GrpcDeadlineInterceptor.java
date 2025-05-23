package io.temporal.serviceclient;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Deadline;
import io.grpc.MethodDescriptor;
import io.temporal.api.workflowservice.v1.WorkflowServiceGrpc;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Set RPC call deadlines according to ServiceFactoryOptions. */
class GrpcDeadlineInterceptor implements ClientInterceptor {

  private static final Logger log = LoggerFactory.getLogger(GrpcDeadlineInterceptor.class);

  private final @Nonnull Duration rpcTimeout;
  private final @Nullable Duration rpcLongPollTimeout;
  private final @Nullable Duration rpcQueryTimeout;

  public GrpcDeadlineInterceptor(
      @Nonnull Duration rpcTimeout,
      @Nullable Duration rpcLongPollTimeout,
      @Nullable Duration rpcQueryTimeout) {
    this.rpcTimeout = rpcTimeout;
    this.rpcLongPollTimeout = rpcLongPollTimeout;
    this.rpcQueryTimeout = rpcQueryTimeout;
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
    long duration;

    if (rpcLongPollTimeout != null && LongPollUtil.isLongPoll(method, callOptions)) {
      duration = rpcLongPollTimeout.toMillis();
    } else if (rpcQueryTimeout != null && method == WorkflowServiceGrpc.getQueryWorkflowMethod()) {
      duration = rpcQueryTimeout.toMillis();
    } else {
      duration = rpcTimeout.toMillis();
    }

    Deadline deadline = callOptions.getDeadline();
    if (deadline != null) {
      duration = Math.min(duration, deadline.timeRemaining(TimeUnit.MILLISECONDS));
    }

    if (log.isTraceEnabled()) {
      String name = method.getFullMethodName();
      log.trace("method=" + name + ", timeoutMs=" + duration);
    }
    return next.newCall(method, callOptions.withDeadlineAfter(duration, TimeUnit.MILLISECONDS));
  }
}
