package io.temporal.internal.sync;

/**
 * A workflow tasks are allowed to execute only some limited amount of time without interruption. If
 * workflow task runs longer than specified interval without yielding (like calling an Activity), it
 * will fail automatically with this exceptions. This is done to detect deadlocks in workflows and
 * based on the assumptions that workflow code shouldn't be blocking.
 */
public class PotentialDeadlockException extends RuntimeException {

  private final WorkflowThreadContext workflowThreadContext;
  private final long detectionTimestamp;

  private String triggerThreadStackTrace;
  private String otherThreadsDump;
  private long threadDumpTimestamp;

  /**
   * Note: because of JVM checkpoints, a timestamp when SDK detects a deadlock and a timestamp when
   * it's able to take a thread's stacktrace may differ. It may produce misleading stacktraces when
   * the thread stacktrace is taken from a different point then where it was originally when the
   * deadlock detection fired. This can give a false lead to the investigator. For that reason, the
   * exception message includes a timestamp when the deadlock was detected and a timestamp when the
   * tread dump was taken to provide more context and make this tricky scenario more obvious.
   *
   * @param threadName name of the thread that is in a potential deadlock state
   * @param workflowThreadContext context of the thread that is in a potential deadlock state
   * @param detectionTimestamp a timestamp the deadlock was detected
   */
  PotentialDeadlockException(
      String threadName, WorkflowThreadContext workflowThreadContext, long detectionTimestamp) {
    super(
        "[TMPRL1101] Potential deadlock detected. Workflow thread \""
            + threadName
            + "\" didn't yield control for over a second.");
    this.workflowThreadContext = workflowThreadContext;
    this.detectionTimestamp = detectionTimestamp;
  }

  /**
   * @param triggerThreadStackTrace stacktrace of the thread that triggered the Deadlock Detector
   * @param otherThreadsDump stack dump of other threads of the workflow excluding the thread that
   *     triggered the deadlock detector
   * @param threadDumpTimestamp timestamp when the thread dump was taken
   */
  void setStackDump(
      String triggerThreadStackTrace, String otherThreadsDump, long threadDumpTimestamp) {
    this.triggerThreadStackTrace = triggerThreadStackTrace;
    this.otherThreadsDump = otherThreadsDump;
    this.threadDumpTimestamp = threadDumpTimestamp;
  }

  @Override
  public String getMessage() {
    return super.getMessage()
        + " {"
        + ("detectionTimestamp=" + detectionTimestamp)
        + (", threadDumpTimestamp=" + threadDumpTimestamp)
        + "} \n\n"
        + triggerThreadStackTrace
        + (!otherThreadsDump.isEmpty()
            ? "\n Other workflow threads: [\n" + otherThreadsDump + "]\n"
            : "");
  }

  public WorkflowThreadContext getWorkflowThreadContext() {
    return this.workflowThreadContext;
  }
}
