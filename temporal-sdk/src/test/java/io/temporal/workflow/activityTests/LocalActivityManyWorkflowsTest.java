package io.temporal.workflow.activityTests;

import static org.junit.Assert.assertEquals;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.LocalActivityOptions;
import io.temporal.testing.internal.SDKTestWorkflowRule;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.shared.TestWorkflows.TestWorkflow1;
import java.time.Duration;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;

public class LocalActivityManyWorkflowsTest {

  @Rule
  public SDKTestWorkflowRule testWorkflowRule =
      SDKTestWorkflowRule.newBuilder()
          .setActivityImplementations(new ActivityImpl())
          .setWorkflowTypes(ActivityWorkflow.class)
          .build();

  @Test
  public void manyWorkflowsTest() {
    Assume.assumeFalse("skipping for docker tests", testWorkflowRule.isUseExternalService());

    for (int reqCount = 1; reqCount < 100; reqCount++) {
      TestWorkflow1 workflow = testWorkflowRule.newWorkflowStub(TestWorkflow1.class);
      String input = String.valueOf(reqCount);
      String result = workflow.execute(input);
      assertEquals(input + "31", result);
    }
  }

  @ActivityInterface
  public interface TestActivity {
    String activity(String input);
  }

  public static class ActivityWorkflow implements TestWorkflow1 {
    private final TestActivity activity =
        Workflow.newLocalActivityStub(
            TestActivity.class,
            LocalActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(2))
                .build());

    @Override
    public String execute(String input) {
      return activity.activity(input + "3");
    }
  }

  private static class ActivityImpl implements TestActivity {
    @Override
    public String activity(String input) {
      return input + "1";
    }
  }
}
