package io.temporal.internal.sync;

import static org.junit.Assert.*;

import io.temporal.client.WorkflowOptions;
import io.temporal.failure.CanceledFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.workflow.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.*;

@SuppressWarnings("deprecation")
public class WorkflowInternalDeprecatedQueueTest {

  @Rule public final Tracer trace = new Tracer();

  private static ExecutorService threadPool;

  @BeforeClass
  public static void beforeClass() {
    threadPool = new ThreadPoolExecutor(1, 1000, 1, TimeUnit.SECONDS, new SynchronousQueue<>());
  }

  @AfterClass
  public static void afterClass() {
    threadPool.shutdown();
  }

  @Test
  public void testTakeBlocking() {
    DeterministicRunner r =
        DeterministicRunner.newRunner(
            threadPool::submit,
            DummySyncWorkflowContext.newDummySyncWorkflowContext(),
            () -> {
              WorkflowQueue<Boolean> f = WorkflowInternal.newQueue(1);
              trace.add("root begin");
              WorkflowThread.newThread(
                      () -> {
                        trace.add("thread1 begin");
                        assertTrue(f.take());
                        trace.add("thread1 take success");
                      },
                      false)
                  .start();
              WorkflowThread.newThread(
                      () -> {
                        trace.add("thread2 begin");
                        f.put(true);
                        trace.add("thread2 put success");
                      },
                      false)
                  .start();
              trace.add("root done");
            });
    r.runUntilAllBlocked(DeterministicRunner.DEFAULT_DEADLOCK_DETECTION_TIMEOUT_MS);
    String[] expected =
        new String[] {
          "root begin",
          "root done",
          "thread1 begin",
          "thread2 begin",
          "thread2 put success",
          "thread1 take success",
        };
    trace.setExpected(expected);
    r.close();
  }

  @Test
  public void testTakeCanceled() {
    DeterministicRunner r =
        DeterministicRunner.newRunner(
            threadPool::submit,
            DummySyncWorkflowContext.newDummySyncWorkflowContext(),
            () -> {
              WorkflowQueue<Boolean> f = WorkflowInternal.newQueue(1);
              trace.add("root begin");
              WorkflowThread.newThread(
                      () -> {
                        trace.add("thread1 begin");
                        try {
                          assertTrue(f.take());
                        } catch (CanceledFailure e) {
                          trace.add("thread1 CanceledException");
                        }
                        trace.add("thread1 done");
                      },
                      false)
                  .start();
              trace.add("root done");
            });
    r.runUntilAllBlocked(DeterministicRunner.DEFAULT_DEADLOCK_DETECTION_TIMEOUT_MS);
    r.cancel("test");
    r.runUntilAllBlocked(DeterministicRunner.DEFAULT_DEADLOCK_DETECTION_TIMEOUT_MS);

    String[] expected =
        new String[] {
          "root begin", "root done", "thread1 begin",
        };
    trace.setExpected(expected);
    r.close();
  }

  @Test
  public void testCancellableTakeCanceled() {
    DeterministicRunner r =
        DeterministicRunner.newRunner(
            threadPool::submit,
            DummySyncWorkflowContext.newDummySyncWorkflowContext(),
            () -> {
              WorkflowQueue<Boolean> f = WorkflowInternal.newQueue(1);
              trace.add("root begin");
              WorkflowThread.newThread(
                      () -> {
                        trace.add("thread1 begin");
                        try {
                          assertTrue(f.cancellableTake());
                        } catch (CanceledFailure e) {
                          trace.add("thread1 CanceledFailure");
                        }
                        trace.add("thread1 done");
                      },
                      false)
                  .start();
              trace.add("root done");
            });
    r.runUntilAllBlocked(DeterministicRunner.DEFAULT_DEADLOCK_DETECTION_TIMEOUT_MS);
    r.cancel("test");
    r.runUntilAllBlocked(DeterministicRunner.DEFAULT_DEADLOCK_DETECTION_TIMEOUT_MS);

    String[] expected =
        new String[] {
          "root begin", "root done", "thread1 begin", "thread1 CanceledFailure", "thread1 done",
        };
    trace.setExpected(expected);
  }

  @WorkflowInterface
  public interface WorkflowQueueTestWorkflow {
    @WorkflowMethod
    List<String> test();
  }

  public static class TestPutBlocking implements WorkflowQueueTestWorkflow {

    @Override
    public List<String> test() {
      List<String> trace = new ArrayList<>();

      WorkflowQueue<Boolean> f = WorkflowInternal.newQueue(1);
      trace.add("root begin");
      WorkflowThread thread1 =
          WorkflowThread.newThread(
              () -> {
                trace.add("thread1 begin");
                Workflow.sleep(2000);
                assertTrue(f.take());
                trace.add("thread1 take1 success");
                assertFalse(f.take());
                trace.add("thread1 take2 success");
              },
              false);

      thread1.start();
      WorkflowThread thread2 =
          WorkflowThread.newThread(
              () -> {
                trace.add("thread2 begin");
                f.put(true);
                trace.add("thread2 put1 success");
                f.put(false);
                trace.add("thread2 put2 success");
              },
              false);
      thread2.start();
      trace.add("root done");
      Workflow.await(() -> thread1.isDone() && thread2.isDone());
      return trace;
    }
  }

  @Test
  public void testPutBlocking() {
    TestWorkflowEnvironment testEnv = TestWorkflowEnvironment.newInstance();
    try {
      String testTaskQueue = "testTaskQueue";
      Worker worker = testEnv.newWorker(testTaskQueue);
      worker.registerWorkflowImplementationTypes(TestPutBlocking.class);
      testEnv.start();
      WorkflowQueueTestWorkflow workflow =
          testEnv
              .getWorkflowClient()
              .newWorkflowStub(
                  WorkflowQueueTestWorkflow.class,
                  WorkflowOptions.newBuilder().setTaskQueue(testTaskQueue).build());
      List<String> trace = workflow.test();
      List<String> expected =
          Arrays.asList(
              "root begin",
              "root done",
              "thread1 begin",
              "thread2 begin",
              "thread2 put1 success",
              "thread1 take1 success",
              "thread2 put2 success",
              "thread1 take2 success");
      assertEquals(expected, trace);
    } finally {
      testEnv.close();
    }
  }

  public static class TestOfferPollPeek implements WorkflowQueueTestWorkflow {

    @Override
    public List<String> test() {
      List<String> trace = new ArrayList<>();
      WorkflowQueue<Integer> f = WorkflowInternal.newQueue(1);
      trace.add("root begin");
      trace.add("peek " + f.peek());
      trace.add("offer " + f.offer(12));
      trace.add("offer " + f.offer(21));
      trace.add("peek " + f.peek());
      trace.add("poll " + f.poll());
      trace.add("offer " + f.offer(23));
      trace.add("offer " + f.offer(34, Duration.ofSeconds(100)));
      trace.add("take " + f.take());
      trace.add("root done");

      return trace;
    }
  }

  @Test
  public void testOfferPollPeek() {
    TestWorkflowEnvironment testEnv = TestWorkflowEnvironment.newInstance();
    try {
      String testTaskQueue = "testTaskQueue";
      Worker worker = testEnv.newWorker(testTaskQueue);
      worker.registerWorkflowImplementationTypes(TestOfferPollPeek.class);
      testEnv.start();
      WorkflowQueueTestWorkflow workflow =
          testEnv
              .getWorkflowClient()
              .newWorkflowStub(
                  WorkflowQueueTestWorkflow.class,
                  WorkflowOptions.newBuilder().setTaskQueue(testTaskQueue).build());
      List<String> trace = workflow.test();
      List<String> expected =
          Arrays.asList(
              "root begin",
              "peek null",
              "offer true",
              "offer false",
              "peek 12",
              "poll 12",
              "offer true",
              "offer false",
              "take 23",
              "root done");
      assertEquals(expected, trace);
    } finally {
      testEnv.close();
    }
  }

  @Test
  public void testPutCanceled() {
    DeterministicRunner r =
        DeterministicRunner.newRunner(
            threadPool::submit,
            DummySyncWorkflowContext.newDummySyncWorkflowContext(),
            () -> {
              WorkflowQueue<Boolean> f = WorkflowInternal.newQueue(1);
              trace.add("root begin");
              WorkflowThread.newThread(
                      () -> {
                        trace.add("thread1 begin");
                        try {
                          f.put(true);
                          f.put(true);
                        } catch (CanceledFailure e) {
                          trace.add("thread1 CanceledFailure");
                        }
                        trace.add("thread1 done");
                      },
                      false)
                  .start();
              trace.add("root done");
            });
    r.runUntilAllBlocked(DeterministicRunner.DEFAULT_DEADLOCK_DETECTION_TIMEOUT_MS);
    r.cancel("test");
    r.runUntilAllBlocked(DeterministicRunner.DEFAULT_DEADLOCK_DETECTION_TIMEOUT_MS);

    String[] expected =
        new String[] {
          "root begin", "root done", "thread1 begin",
        };
    trace.setExpected(expected);
    r.close();
  }

  @Test
  public void testCancellablePutCanceled() {
    DeterministicRunner r =
        DeterministicRunner.newRunner(
            threadPool::submit,
            DummySyncWorkflowContext.newDummySyncWorkflowContext(),
            () -> {
              WorkflowQueue<Boolean> f = WorkflowInternal.newQueue(1);
              trace.add("root begin");
              WorkflowThread.newThread(
                      () -> {
                        trace.add("thread1 begin");
                        try {
                          f.put(true);
                          f.cancellablePut(true);
                        } catch (CanceledFailure e) {
                          trace.add("thread1 CanceledFailure");
                        }
                        trace.add("thread1 done");
                      },
                      false)
                  .start();
              trace.add("root done");
            });
    r.runUntilAllBlocked(DeterministicRunner.DEFAULT_DEADLOCK_DETECTION_TIMEOUT_MS);
    r.cancel("test");
    r.runUntilAllBlocked(DeterministicRunner.DEFAULT_DEADLOCK_DETECTION_TIMEOUT_MS);

    String[] expected =
        new String[] {
          "root begin", "root done", "thread1 begin", "thread1 CanceledFailure", "thread1 done",
        };
    trace.setExpected(expected);
    r.close();
  }

  @Test
  public void testMap() {
    DeterministicRunner r =
        DeterministicRunner.newRunner(
            threadPool::submit,
            DummySyncWorkflowContext.newDummySyncWorkflowContext(),
            () -> {
              WorkflowQueue<Integer> queue = WorkflowInternal.newQueue(1);
              trace.add("root begin");
              WorkflowThread.newThread(
                      () -> {
                        QueueConsumer<String> mapped = queue.map((s) -> s + "-mapped");
                        trace.add("thread1 begin");
                        for (int i = 0; i < 10; i++) {
                          trace.add("thread1 " + mapped.take());
                        }
                        trace.add("thread1 done");
                      },
                      false)
                  .start();
              trace.add("root thread1 started");
              for (int i = 0; i < 10; i++) {
                queue.put(i);
              }
              trace.add("root done");
            });
    r.runUntilAllBlocked(DeterministicRunner.DEFAULT_DEADLOCK_DETECTION_TIMEOUT_MS);
    r.cancel("test");
    r.runUntilAllBlocked(DeterministicRunner.DEFAULT_DEADLOCK_DETECTION_TIMEOUT_MS);

    String[] expected =
        new String[] {
          "root begin",
          "root thread1 started",
          "thread1 begin",
          "thread1 0-mapped",
          "thread1 1-mapped",
          "thread1 2-mapped",
          "thread1 3-mapped",
          "thread1 4-mapped",
          "thread1 5-mapped",
          "thread1 6-mapped",
          "thread1 7-mapped",
          "thread1 8-mapped",
          "root done",
          "thread1 9-mapped",
          "thread1 done",
        };
    trace.setExpected(expected);
    r.close();
  }
}
