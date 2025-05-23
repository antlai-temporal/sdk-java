package io.temporal.workflow;

import static org.junit.Assert.assertTrue;

import io.temporal.client.WorkflowOptions;
import io.temporal.testing.internal.SDKTestOptions;
import io.temporal.testing.internal.SDKTestWorkflowRule;
import io.temporal.testing.internal.TracingWorkerInterceptor;
import io.temporal.workflow.shared.TestWorkflows.TestTraceWorkflow;
import java.time.Duration;
import java.util.List;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class TimerTest {

  @Rule
  public SDKTestWorkflowRule testWorkflowRule =
      SDKTestWorkflowRule.newBuilder().setWorkflowTypes(TestTimerWorkflowImpl.class).build();

  @Test
  public void testTimer() {
    WorkflowOptions options;
    if (testWorkflowRule.isUseExternalService()) {
      options = SDKTestOptions.newWorkflowOptionsWithTimeouts(testWorkflowRule.getTaskQueue());
    } else {
      options =
          SDKTestOptions.newWorkflowOptionsWithTimeouts(testWorkflowRule.getTaskQueue()).toBuilder()
              .setWorkflowRunTimeout(Duration.ofDays(1))
              .build();
    }
    TestTraceWorkflow client =
        testWorkflowRule.getWorkflowClient().newWorkflowStub(TestTraceWorkflow.class, options);
    String result = client.execute();
    Assert.assertEquals("testTimer", result);
    if (testWorkflowRule.isUseExternalService()) {
      testWorkflowRule
          .getInterceptor(TracingWorkerInterceptor.class)
          .setExpected(
              "interceptExecuteWorkflow " + SDKTestWorkflowRule.UUID_REGEXP,
              "registerQuery getTrace",
              "newThread workflow-method",
              "newTimer PT0.7S",
              "newTimer PT1.3S",
              "currentTimeMillis",
              "newTimer PT10S",
              "currentTimeMillis",
              "currentTimeMillis",
              "currentTimeMillis");
    } else {
      testWorkflowRule
          .getInterceptor(TracingWorkerInterceptor.class)
          .setExpected(
              "interceptExecuteWorkflow " + SDKTestWorkflowRule.UUID_REGEXP,
              "registerQuery getTrace",
              "newThread workflow-method",
              "newTimer PT11M40S",
              "newTimer PT21M40S",
              "currentTimeMillis",
              "newTimer PT10H",
              "currentTimeMillis",
              "currentTimeMillis",
              "currentTimeMillis");
    }
  }

  public static class TestTimerWorkflowImpl implements TestTraceWorkflow {

    @Override
    public String execute() {
      Promise<Void> timer1;
      Promise<Void> timer2;
      boolean useExternalService = SDKTestWorkflowRule.useExternalService;
      Duration timeout1 = useExternalService ? Duration.ofMillis(700) : Duration.ofSeconds(700);
      Duration timeout2 = useExternalService ? Duration.ofMillis(1300) : Duration.ofSeconds(1300);
      timer1 = Workflow.newTimer(timeout1);
      timer2 = Workflow.newTimer(timeout2);
      long time = Workflow.currentTimeMillis();
      timer1
          .thenApply(
              r -> {
                // Testing that timer can be created from a callback thread.
                if (useExternalService) {
                  Workflow.newTimer(Duration.ofSeconds(10));
                } else {
                  Workflow.newTimer(Duration.ofHours(10));
                }
                Workflow.currentTimeMillis(); // Testing that time is available here.
                return r;
              })
          .get();
      timer1.get();
      long slept = Workflow.currentTimeMillis() - time;
      // Also checks that rounding up to a second works.
      assertTrue(slept + "<" + timeout1.toMillis(), slept >= timeout1.toMillis());
      timer2.get();
      slept = Workflow.currentTimeMillis() - time;
      assertTrue(String.valueOf(slept), slept >= timeout2.toMillis());
      return "testTimer";
    }

    @Override
    public List<String> getTrace() {
      throw new UnsupportedOperationException("not implemented");
    }
  }
}
