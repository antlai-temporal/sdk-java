package io.temporal.workflow;

import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.internal.sync.StubMarker;
import java.lang.reflect.Type;

/**
 * Supports starting and signalling child workflows by the name and list of arguments. This is
 * useful when a child workflow type is not known at the compile time and to call child workflows in
 * other languages.
 *
 * @see Workflow#newChildWorkflowStub(Class)
 */
public interface ChildWorkflowStub {

  /**
   * Extracts untyped WorkflowStub from a typed workflow stub created through {@link
   * Workflow#newChildWorkflowStub(Class)}.
   *
   * @param typed typed workflow stub
   * @param <T> type of the workflow stub interface
   * @return untyped workflow stub for the same workflow instance.
   */
  static <T> ChildWorkflowStub fromTyped(T typed) {
    if (!(typed instanceof StubMarker)) {
      throw new IllegalArgumentException(
          "arguments must be created through Workflow.newChildWorkflowStub");
    }
    if (typed instanceof ExternalWorkflowStub) {
      throw new IllegalArgumentException(
          "Use ExternalWorkflowStub.fromTyped to extract stub created through Workflow#newExternalWorkflowStub");
    }
    @SuppressWarnings("unchecked")
    StubMarker supplier = (StubMarker) typed;
    return (ChildWorkflowStub) supplier.__getUntypedStub();
  }

  String getWorkflowType();

  /**
   * If workflow completes before this promise is ready then the child might not start at all.
   *
   * @return promise that becomes ready once the child has started.
   */
  Promise<WorkflowExecution> getExecution();

  ChildWorkflowOptions getOptions();

  <R> R execute(Class<R> resultClass, Object... args);

  <R> R execute(Class<R> resultClass, Type resultType, Object... args);

  <R> Promise<R> executeAsync(Class<R> resultClass, Object... args);

  <R> Promise<R> executeAsync(Class<R> resultClass, Type resultType, Object... args);

  void signal(String signalName, Object... args);
}
