package io.temporal.common;

import io.temporal.activity.ActivityOptions;
import io.temporal.failure.ApplicationFailure;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;

/**
 * Specifies a retry policy for a workflow or activity method. This annotation applies only to
 * activity or workflow interface methods. For workflows currently used only for child workflow
 * retries. Not required. When not used either retries don't happen or they are configured through
 * correspondent options. If {@link io.temporal.common.RetryOptions} are present on {@link
 * ActivityOptions} or {@link io.temporal.workflow.ChildWorkflowOptions} the fields that are not
 * default take precedence over parameters of this annotation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MethodRetry {

  /**
   * Interval of the first retry. If coefficient is 1.0 then it is used for all retries. If not
   * specified here must be provided through {@link
   * io.temporal.common.RetryOptions.Builder#setInitialInterval(Duration)}.
   */
  long initialIntervalSeconds() default 0;

  /**
   * Coefficient used to calculate the next retry interval. The next retry interval is previous
   * interval multiplied by this coefficient. Must be 1 or larger. Default is 2.0.
   */
  double backoffCoefficient() default 0;

  /**
   * Maximum number of attempts. When exceeded the retries stop even if not expired yet. Must be 1
   * or bigger. Default is unlimited.
   */
  int maximumAttempts() default 0;

  /**
   * Maximum interval between retries. Exponential backoff leads to interval increase. This value is
   * the cap of the increase. Default is 100x {@link #initialIntervalSeconds()}.
   */
  long maximumIntervalSeconds() default 0;

  /**
   * List of failure types to not retry. The failure type of an exception is its full class name. It
   * can be also explicitly specified by throwing an {@link ApplicationFailure}
   */
  String[] doNotRetry() default {};
}
