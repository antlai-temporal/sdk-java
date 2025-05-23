package io.temporal.common.interceptors;

import io.nexusrpc.handler.OperationContext;
import io.temporal.common.Experimental;

/**
 * Intercepts workflow and activity executions.
 *
 * <p>Prefer extending {@link WorkerInterceptorBase} and overriding only the methods you need
 * instead of implementing this interface directly. {@link WorkerInterceptorBase} provides correct
 * default implementations to all the methods of this interface.
 *
 * <p>You may want to start your implementation with this initial structure:
 *
 * <pre><code>
 * public class CustomWorkerInterceptor extends WorkerInterceptorBase {
 *   // remove if you don't need to have a custom WorkflowInboundCallsInterceptor or
 *   // WorkflowOutboundCallsInterceptor
 *  {@literal @}Override
 *   public WorkflowInboundCallsInterceptor interceptWorkflow(WorkflowInboundCallsInterceptor next) {
 *     return new CustomWorkflowInboundCallsInterceptor(next) {
 *       // remove if you don't need to have a custom WorkflowOutboundCallsInterceptor
 *       {@literal @}Override
 *       public void init(WorkflowOutboundCallsInterceptor outboundCalls) {
 *         next.init(new CustomWorkflowOutboundCallsInterceptor(outboundCalls));
 *       }
 *     };
 *   }
 *
 *   // remove if you don't need to have a custom ActivityInboundCallsInterceptor
 *  {@literal @}Override
 *   public ActivityInboundCallsInterceptor interceptActivity(ActivityInboundCallsInterceptor next) {
 *     return new CustomActivityInboundCallsInterceptor(next);
 *   }
 *
 *   private static class CustomWorkflowInboundCallsInterceptor
 *       extends WorkflowInboundCallsInterceptorBase {
 *     public CustomWorkflowInboundCallsInterceptor(WorkflowInboundCallsInterceptor next) {
 *       super(next);
 *     }
 *
 *     // override only the methods you need
 *   }
 *
 *   private static class CustomWorkflowOutboundCallsInterceptor
 *       extends WorkflowOutboundCallsInterceptorBase {
 *     public CustomWorkflowOutboundCallsInterceptor(WorkflowOutboundCallsInterceptor next) {
 *       super(next);
 *     }
 *
 *     // override only the methods you need
 *   }
 *
 *   private static class CustomActivityInboundCallsInterceptor
 *       extends ActivityInboundCallsInterceptorBase {
 *     public CustomActivityInboundCallsInterceptor(ActivityInboundCallsInterceptor next) {
 *       super(next);
 *     }
 *
 *     // override only the methods you need
 *   }
 * }
 * </code></pre>
 */
@Experimental
public interface WorkerInterceptor {
  /**
   * Called when workflow class is instantiated. May create a {@link
   * WorkflowInboundCallsInterceptor} instance. The instance must forward all the calls to {@code
   * next} {@link WorkflowInboundCallsInterceptor}, but it may change the input parameters.
   *
   * @param next an existing interceptor instance to be proxied by the interceptor created inside
   *     this method
   * @return an interceptor that passes all the calls to {@code next}
   */
  WorkflowInboundCallsInterceptor interceptWorkflow(WorkflowInboundCallsInterceptor next);

  ActivityInboundCallsInterceptor interceptActivity(ActivityInboundCallsInterceptor next);

  /**
   * Called when Nexus task is received. May create a {@link NexusOperationInboundCallsInterceptor}
   * instance. The instance must forward all the calls to {@code next} {@link
   * NexusOperationInboundCallsInterceptor}, but it may change the input parameters.
   *
   * @param next an existing interceptor instance to be proxied by the interceptor created inside
   *     this method
   * @return an interceptor that passes all the calls to {@code next}
   */
  NexusOperationInboundCallsInterceptor interceptNexusOperation(
      OperationContext context, NexusOperationInboundCallsInterceptor next);
}
