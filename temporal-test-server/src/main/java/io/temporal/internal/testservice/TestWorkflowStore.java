package io.temporal.internal.testservice;

import com.google.protobuf.Timestamp;
import io.grpc.Deadline;
import io.temporal.api.common.v1.Priority;
import io.temporal.api.workflow.v1.WorkflowExecutionInfo;
import io.temporal.api.workflowservice.v1.*;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Future;

interface TestWorkflowStore {

  enum WorkflowState {
    OPEN,
    CLOSED
  }

  long BUFFERED_EVENT_ID = -123L;

  class TaskQueueId {

    private final String namespace;
    private final String taskQueueName;

    public TaskQueueId(String namespace, String taskQueueName) {
      this.namespace = Objects.requireNonNull(namespace);
      this.taskQueueName = Objects.requireNonNull(taskQueueName);
    }

    public String getNamespace() {
      return namespace;
    }

    public String getTaskQueueName() {
      return taskQueueName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }

      if (!(o instanceof TaskQueueId)) {
        return false;
      }

      TaskQueueId that = (TaskQueueId) o;

      if (!namespace.equals(that.namespace)) {
        return false;
      }
      return taskQueueName.equals(that.taskQueueName);
    }

    @Override
    public int hashCode() {
      int result = namespace.hashCode();
      result = 31 * result + taskQueueName.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return "TaskQueueId{"
          + "namespace='"
          + namespace
          + '\''
          + ", taskQueueName='"
          + taskQueueName
          + '\''
          + '}';
    }
  }

  class WorkflowTask {

    private final TaskQueueId taskQueueId;
    private final PollWorkflowTaskQueueResponse.Builder task;

    public WorkflowTask(TaskQueueId taskQueueId, PollWorkflowTaskQueueResponse.Builder task) {
      this.taskQueueId = taskQueueId;
      this.task = task;
    }

    public TaskQueueId getTaskQueueId() {
      return taskQueueId;
    }

    public PollWorkflowTaskQueueResponse.Builder getTask() {
      return task;
    }
  }

  class ActivityTask {

    private final TaskQueueId taskQueueId;
    private final PollActivityTaskQueueResponse.Builder task;

    public ActivityTask(TaskQueueId taskQueueId, PollActivityTaskQueueResponse.Builder task) {
      this.taskQueueId = taskQueueId;
      this.task = task;
    }

    public TaskQueueId getTaskQueueId() {
      return taskQueueId;
    }

    public PollActivityTaskQueueResponse.Builder getTask() {
      return task;
    }
  }

  class NexusTask {
    private final TaskQueueId taskQueueId;
    private final PollNexusTaskQueueResponse.Builder task;
    private Timestamp deadline;

    public NexusTask(
        TaskQueueId taskQueueId, PollNexusTaskQueueResponse.Builder task, Timestamp deadline) {
      this.taskQueueId = taskQueueId;
      this.task = task;
      this.deadline = deadline;
    }

    public TaskQueueId getTaskQueueId() {
      return taskQueueId;
    }

    public PollNexusTaskQueueResponse.Builder getTask() {
      return task;
    }

    public Timestamp getDeadline() {
      return deadline;
    }

    public void setDeadline(Timestamp deadline) {
      this.deadline = deadline;
    }
  }

  Timestamp currentTime();

  long save(RequestContext requestContext);

  void applyTimersAndLocks(RequestContext ctx);

  void registerDelayedCallback(Duration delay, Runnable r);

  /**
   * @return empty if this store is closed or thread interrupted
   */
  Future<PollWorkflowTaskQueueResponse.Builder> pollWorkflowTaskQueue(
      PollWorkflowTaskQueueRequest pollRequest);

  /**
   * @return empty if this store is closed or thread interrupted
   */
  Future<PollActivityTaskQueueResponse.Builder> pollActivityTaskQueue(
      PollActivityTaskQueueRequest pollRequest);

  Future<NexusTask> pollNexusTaskQueue(PollNexusTaskQueueRequest pollRequest);

  void sendQueryTask(
      ExecutionId executionId,
      TaskQueueId taskQueue,
      PollWorkflowTaskQueueResponse.Builder task,
      Priority priority);

  GetWorkflowExecutionHistoryResponse getWorkflowExecutionHistory(
      ExecutionId executionId,
      GetWorkflowExecutionHistoryRequest getRequest,
      Deadline deadlineToReturnEmptyResponse);

  void getDiagnostics(StringBuilder result);

  List<WorkflowExecutionInfo> listWorkflows(WorkflowState state, Optional<String> workflowId);

  void close();
}
