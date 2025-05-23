package io.temporal.internal.sync;

import static io.temporal.internal.common.InternalUtils.getValueOrDefault;

import io.temporal.common.interceptors.WorkflowOutboundCallsInterceptor;
import io.temporal.common.metadata.POJOWorkflowInterfaceMetadata;
import io.temporal.workflow.ContinueAsNewOptions;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

class ContinueAsNewWorkflowInvocationHandler implements InvocationHandler {
  private final @Nullable ContinueAsNewOptions options;
  private final WorkflowOutboundCallsInterceptor outboundCallsInterceptor;
  private final POJOWorkflowInterfaceMetadata workflowMetadata;

  ContinueAsNewWorkflowInvocationHandler(
      Class<?> interfaceClass,
      @Nullable ContinueAsNewOptions options,
      WorkflowOutboundCallsInterceptor outboundCallsInterceptor) {
    workflowMetadata = POJOWorkflowInterfaceMetadata.newInstance(interfaceClass);
    if (!workflowMetadata.getWorkflowMethod().isPresent()) {
      throw new IllegalArgumentException(
          "Missing method annotated with @WorkflowMethod: " + interfaceClass.getName());
    }
    this.options = options;
    this.outboundCallsInterceptor = outboundCallsInterceptor;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) {
    String workflowType = workflowMetadata.getMethodMetadata(method).getName();
    WorkflowInternal.continueAsNew(workflowType, options, args, outboundCallsInterceptor);
    return getValueOrDefault(null, method.getReturnType());
  }
}
