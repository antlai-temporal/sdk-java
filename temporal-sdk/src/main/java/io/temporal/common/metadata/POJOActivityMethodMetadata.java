package io.temporal.common.metadata;

import com.google.common.base.Strings;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import java.lang.reflect.Method;
import java.util.Objects;

/** Metadata of a single activity method. */
public final class POJOActivityMethodMetadata {
  private final String name;
  private final Method method;
  private final Class<?> interfaceType;

  POJOActivityMethodMetadata(
      Method method, Class<?> interfaceType, ActivityInterface activityAnnotation) {
    this.method = Objects.requireNonNull(method);
    this.interfaceType = Objects.requireNonNull(interfaceType);
    ActivityMethod activityMethod = method.getAnnotation(ActivityMethod.class);
    this.name =
        activityMethod != null && !activityMethod.name().isEmpty()
            ? activityMethod.name()
            : activityAnnotation.namePrefix() + getActivityNameFromMethod(method);
  }

  // Capitalize the first letter
  // TODO(maxim): make activity name generation pluggable through options
  private static String getActivityNameFromMethod(Method method) {
    String name = method.getName();
    return name.substring(0, 1).toUpperCase() + name.substring(1);
  }

  /** Name of activity type that this method implements */
  public String getActivityTypeName() {
    if (Strings.isNullOrEmpty(name)) {
      throw new IllegalStateException("Not annotated: " + method);
    }
    return name;
  }

  /** Interface method that defines the activity. */
  public Method getMethod() {
    return method;
  }

  /** Activity interface that this method belongs to. */
  Class<?> getInterfaceType() {
    return interfaceType;
  }

  /** Compare and hash based on method and the interface type only. */
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    POJOActivityMethodMetadata that = (POJOActivityMethodMetadata) o;
    return com.google.common.base.Objects.equal(method, that.method)
        && com.google.common.base.Objects.equal(interfaceType, that.interfaceType);
  }

  /** Compare and hash based on method and the interface type only. */
  @Override
  public int hashCode() {
    return com.google.common.base.Objects.hashCode(method, interfaceType);
  }
}
