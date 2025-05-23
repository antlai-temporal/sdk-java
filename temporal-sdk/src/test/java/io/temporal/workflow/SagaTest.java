package io.temporal.workflow;

import io.temporal.common.RetryOptions;
import io.temporal.testing.internal.SDKTestOptions;
import io.temporal.testing.internal.SDKTestWorkflowRule;
import io.temporal.testing.internal.TracingWorkerInterceptor;
import io.temporal.workflow.shared.TestActivities.TestActivitiesImpl;
import io.temporal.workflow.shared.TestActivities.VariousTestActivities;
import io.temporal.workflow.shared.TestMultiArgWorkflowFunctions.TestNoArgsWorkflowFunc;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class SagaTest {

  @Rule
  public SDKTestWorkflowRule testWorkflowRule =
      SDKTestWorkflowRule.newBuilder()
          .setWorkflowTypes(
              TestSagaWorkflowImpl.class,
              TestCompensationWorkflowImpl.class,
              TestNoArgsWorkflowsFuncImpl.class)
          .setActivityImplementations(new TestActivitiesImpl())
          .build();

  @Test
  public void testSaga() {
    TestSagaWorkflow sagaWorkflow =
        testWorkflowRule.newWorkflowStubTimeoutOptions(TestSagaWorkflow.class);
    sagaWorkflow.execute(testWorkflowRule.getTaskQueue(), false);
    testWorkflowRule
        .getInterceptor(TracingWorkerInterceptor.class)
        .setExpected(
            "interceptExecuteWorkflow " + SDKTestWorkflowRule.UUID_REGEXP,
            "newThread workflow-method",
            "executeActivity customActivity1",
            "activity customActivity1",
            "executeChildWorkflow TestNoArgsWorkflowFunc",
            "interceptExecuteWorkflow " + SDKTestWorkflowRule.UUID_REGEXP,
            "registerUpdateHandlers update",
            "newThread workflow-method",
            "executeActivity ThrowIO",
            "activity ThrowIO",
            "executeChildWorkflow TestCompensationWorkflow",
            "interceptExecuteWorkflow " + SDKTestWorkflowRule.UUID_REGEXP,
            "newThread workflow-method",
            "executeActivity Activity2",
            "activity Activity2");
  }

  @Test
  public void testSagaParallelCompensation() {
    TestSagaWorkflow sagaWorkflow =
        testWorkflowRule.newWorkflowStubTimeoutOptions(TestSagaWorkflow.class);
    sagaWorkflow.execute(testWorkflowRule.getTaskQueue(), true);
    String trace = testWorkflowRule.getInterceptor(TracingWorkerInterceptor.class).getTrace();
    Assert.assertTrue(trace, trace.contains("executeChildWorkflow TestCompensationWorkflow"));
    Assert.assertTrue(trace, trace.contains("executeActivity Activity2"));
  }

  @WorkflowInterface
  public interface TestSagaWorkflow {
    @WorkflowMethod
    String execute(String taskQueue, boolean parallelCompensation);
  }

  @WorkflowInterface
  public interface TestCompensationWorkflow {
    @WorkflowMethod
    void compensate();
  }

  public static class TestCompensationWorkflowImpl implements TestCompensationWorkflow {
    @Override
    public void compensate() {}
  }

  public static class TestSagaWorkflowImpl implements TestSagaWorkflow {

    @Override
    public String execute(String taskQueue, boolean parallelCompensation) {
      VariousTestActivities testActivities =
          Workflow.newActivityStub(
              VariousTestActivities.class,
              SDKTestOptions.newActivityOptionsForTaskQueue(taskQueue).toBuilder()
                  .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                  .build());

      ChildWorkflowOptions workflowOptions =
          ChildWorkflowOptions.newBuilder().setTaskQueue(taskQueue).build();
      TestNoArgsWorkflowFunc stubF1 =
          Workflow.newChildWorkflowStub(TestNoArgsWorkflowFunc.class, workflowOptions);

      Saga saga =
          new Saga(
              new Saga.Options.Builder().setParallelCompensation(parallelCompensation).build());
      try {
        testActivities.activity1(10);
        saga.addCompensation(testActivities::activity2, "compensate", -10);

        stubF1.func();

        TestCompensationWorkflow compensationWorkflow =
            Workflow.newChildWorkflowStub(TestCompensationWorkflow.class, workflowOptions);
        saga.addCompensation(compensationWorkflow::compensate);

        testActivities.throwIO();
        saga.addCompensation(
            () -> {
              throw new RuntimeException("unreachable");
            });
      } catch (Exception e) {
        saga.compensate();
      }
      return "done";
    }
  }

  public static class TestNoArgsWorkflowsFuncImpl implements TestNoArgsWorkflowFunc {

    @Override
    public String func() {
      return "done";
    }

    @Override
    public String update() {
      throw new UnsupportedOperationException();
    }
  }
}
