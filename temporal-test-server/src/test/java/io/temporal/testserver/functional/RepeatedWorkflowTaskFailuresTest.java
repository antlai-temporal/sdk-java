package io.temporal.testserver.functional;

import com.google.common.collect.ImmutableMap;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.EventType;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.internal.SDKTestWorkflowRule;
import io.temporal.testserver.functional.common.TestWorkflows;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

/**
 * Server should discard repeated workflow task failures and let the workflow task to time out
 *
 * @see <a href="https://github.com/temporalio/temporal/pull/2548">Related Temporal Server PR</a>
 */
public class RepeatedWorkflowTaskFailuresTest {

  private static int retryCount;

  @Rule
  public SDKTestWorkflowRule testWorkflowRule =
      SDKTestWorkflowRule.newBuilder().setWorkflowTypes(TestWorkflow.class).build();

  @Test
  public void repeatedFailure() {
    WorkflowOptions options =
        WorkflowOptions.newBuilder()
            .setWorkflowTaskTimeout(Duration.ofSeconds(1))
            .setTaskQueue(testWorkflowRule.getTaskQueue())
            .build();

    TestWorkflows.PrimitiveWorkflow workflowStub =
        testWorkflowRule
            .getWorkflowClient()
            .newWorkflowStub(TestWorkflows.PrimitiveWorkflow.class, options);
    workflowStub.execute();
    WorkflowExecution execution = WorkflowStub.fromTyped(workflowStub).getExecution();

    Assert.assertEquals(
        "Out of three failed retries only the first one should be FAILED, other 2 should be dropped, allowed to time out and not being recorded at all",
        1,
        testWorkflowRule
            .getHistoryEvents(execution.getWorkflowId(), EventType.EVENT_TYPE_WORKFLOW_TASK_FAILED)
            .size());
  }

  public static class TestWorkflow implements TestWorkflows.PrimitiveWorkflow {

    @SuppressWarnings("deprecation")
    @Override
    public void execute() {
      if (++retryCount < 4) {
        // invalid search attribute type will force the server to fail workflow task
        Workflow.upsertSearchAttributes(ImmutableMap.of("CustomIntField", "string"));
      }
    }
  }
}
