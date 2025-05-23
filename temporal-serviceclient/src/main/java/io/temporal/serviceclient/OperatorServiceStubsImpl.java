package io.temporal.serviceclient;

import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import io.grpc.health.v1.HealthCheckResponse;
import io.temporal.api.operatorservice.v1.OperatorServiceGrpc;
import io.temporal.api.workflowservice.v1.GetSystemInfoResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class OperatorServiceStubsImpl implements OperatorServiceStubs {
  private static final Logger log = LoggerFactory.getLogger(OperatorServiceStubsImpl.class);

  private final ChannelManager channelManager;

  private final OperatorServiceGrpc.OperatorServiceBlockingStub blockingStub;
  private final OperatorServiceGrpc.OperatorServiceFutureStub futureStub;

  /**
   * Creates gRPC Channel and Stubs that connects to the {@link OperatorServiceGrpc} according to
   * the specified options.
   */
  OperatorServiceStubsImpl(OperatorServiceStubsOptions options) {
    ClientInterceptor deadlineInterceptor =
        new GrpcDeadlineInterceptor(options.getRpcTimeout(), null, null);

    this.channelManager =
        new ChannelManager(options, Collections.singletonList(deadlineInterceptor));

    log.info("Created OperatorServiceStubs for channel: {}", channelManager.getRawChannel());

    this.blockingStub = OperatorServiceGrpc.newBlockingStub(channelManager.getInterceptedChannel());
    this.futureStub = OperatorServiceGrpc.newFutureStub(channelManager.getInterceptedChannel());
  }

  @Override
  public ManagedChannel getRawChannel() {
    return channelManager.getRawChannel();
  }

  @Override
  public OperatorServiceGrpc.OperatorServiceBlockingStub blockingStub() {
    return blockingStub;
  }

  @Override
  public OperatorServiceGrpc.OperatorServiceFutureStub futureStub() {
    return futureStub;
  }

  @Override
  public void shutdown() {
    log.info("shutdown");
    channelManager.shutdown();
  }

  @Override
  public void shutdownNow() {
    log.info("shutdownNow");
    channelManager.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return channelManager.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return channelManager.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) {
    return channelManager.awaitTermination(timeout, unit);
  }

  @Override
  public void connect(@Nullable Duration timeout) {
    channelManager.connect(HEALTH_CHECK_SERVICE_NAME, timeout);
  }

  @Override
  public HealthCheckResponse healthCheck() {
    // no need to pass timeout, timeout will be assigned by GrpcDeadlineInterceptor
    return this.channelManager.healthCheck(HEALTH_CHECK_SERVICE_NAME, null);
  }

  @Override
  public Supplier<GetSystemInfoResponse.Capabilities> getServerCapabilities() {
    return this.channelManager.getServerCapabilities();
  }
}
