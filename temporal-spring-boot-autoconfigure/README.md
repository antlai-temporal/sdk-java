# Temporal Spring Boot

The following Readme assumes that you use Spring Boot yml configuration files (`application.yml`).
It should be trivial to adjust if your application uses .properties configuration.
Your application should be a `@SpringBootApplication` 
and have `io.temporal:temporal-spring-boot-starter:${temporalVersion}` added as a dependency.

# Samples

The [Java SDK samples repo](https://github.com/temporalio/samples-java) contains a number of [Spring Boot samples](https://github.com/temporalio/samples-java/tree/main/springboot) that use this module.

# Support

Temporal Spring Boot integration is currently in Public Preview. Users should expect a mostly stable API, but there may be some documentation or features missing.

# Connection setup

The following configuration connects to a locally started Temporal Server 
(see [Temporal Docker Compose](https://github.com/temporalio/docker-compose) or [Temporal CLI](https://docs.temporal.io/cli))

```yml
spring.temporal:
  connection:
    target: local # you can specify a host:port here for a remote connection
    # specifying local is equivalent to WorkflowServiceStubs.newLocalServiceStubs() so all other connection options are ignored. 
    # enable-https: true
  # namespace: default # you can specify a custom namespace that you are using
```

This will be enough to be able to autowire a `WorkflowClient` in your SpringBoot app:

```java
@SpringBootApplication
class App {
  @Autowire
  private WorkflowClient workflowClient;
}
```

If you are working with schedules, you can also autowire `ScheduleClient` in your SpringBoot app:

```java
@SpringBootApplication
class App {
  @Autowire
  private ScheduleClient scheduleClient;
}
```

## mTLS

[Generate PKCS8 or PKCS12 files](https://github.com/temporalio/samples-server/blob/main/tls/client-only/mac/end-entity.sh).
Add the following to your `application.yml` file:

```yml
spring.temporal:
  connection:
    mtls:
      key-file: /path/to/key.key
      cert-chain-file: /path/to/cert.pem # If you use PKCS12 (.pkcs12, .pfx or .p12), you don't need to set it because certificates chain is bundled into the key file
      # key-password: <password_for_the_key>
      # insecure-trust-manager: true # or add ca.pem to java default truststore
      # server-name: <server_name_override> # optional server name overrider, used as authority of ManagedChannelBuilder
```

Alternatively with PKCS8 you can pass the content of the key and certificates chain as strings, which allows to pass them from the environment variable for example:

```yml
spring.temporal:
  connection:
    mtls:
      key: <raw content of the key PEM file>
      cert-chain: <raw content of the cert chain PEM file>
      # key-password: <password_for_the_key>
      # insecure-trust-manager: true # or add ca.pem to java default truststore
```

## API Keys

You can also authenticate with Temporal Cloud using API keys

```yml
spring.temporal:
  connection:
    apiKey: <API key>
```

If an API key is specified, https will automatically be enabled.

## Data Converter

Define a bean of type `io.temporal.common.converter.DataConverter` in Spring context to be used as a custom data converter.
If Spring context has several beans of the `DataConverter` type, the context will fail to start. You need to name one of them `mainDataConverter` to resolve ambiguity.

# Workers configuration

There are two ways of configuring workers. Auto-discovery and an explicit configuration.

## Explicit configuration

Follow the pattern to explicitly configure the workers:

```yml
spring.temporal:
  workers:
    - task-queue: your-task-queue-name
      name: your-worker-name # unique name of the Worker. If not specified, Task Queue is used as the Worker name.
      workflow-classes:
        - your.package.YouWorkflowImpl
      activity-beans:
        - activity-bean-name1
```

<details>
  <summary>Extended Workers configuration example</summary>

  ```yml
  spring.temporal:
    workers:
      - task-queue: your-task-queue-name
        # name: your-worker-name # unique name of the Worker. If not specified, Task Queue is used as the Worker name.
        workflow-classes:
          - your.package.YouWorkflowImpl
        activity-beans:
          - activity-bean-name1
        nexus-service-beans:
          - nexus-service-bean-name1
        # capacity:
          # max-concurrent-workflow-task-executors: 200
          # max-concurrent-activity-executors: 200
          # max-concurrent-local-activity-executors: 200
          # max-concurrent-workflow-task-pollers: 5
          # max-concurrent-activity-task-pollers: 5
        # virtual-thread:
          # using-virtual-threads: true # only supported if JDK 21 or newer is used
        # rate-limits:
          # max-worker-activities-per-second: 5.0
          # max-task-queue-activities-per-second: 5.0
        # build-id:
          # worker-build-id: "1.0.0"
    # workflow-cache:
      # max-instances: 600
      # max-threads: 600
      # using-virtual-workflow-threads: true # only supported if JDK 21 or newer is used
    # start-workers: false # disable auto-start of WorkersFactory and Workers if you want to make any custom changes before the start
```
</details>

## Auto-discovery

Allows to skip specifying Workflow classes, Activity beans, and Nexus Service beans explicitly in the config
by referencing Worker Task Queue names or Worker Names on Workflow, Activity implementations, and Nexus Service implementations.
Auto-discovery is applied after and on top of an explicit configuration.

Add the following to your `application.yml` to auto-discover workflows and activities from your classpath.

```yml
spring.temporal:
  workers-auto-discovery:
    packages:
      - your.package # enumerate all the packages that contain your workflow, activity implementations, and nexus service implementations.
```

What is auto-discovered:
- Workflows implementation classes annotated with `io.temporal.spring.boot.WorkflowImpl`
- Activity beans present Spring context whose implementations are annotated with `io.temporal.spring.boot.ActivityImpl`
- Nexus Service beans present in Spring context whose implementations are annotated with `io.temporal.spring.boot.NexusServiceImpl`
- Workers if a Task Queue is referenced by the annotations but not explicitly configured. Default configuration will be used.

Auto-discovered workflow implementation classes, activity beans, and nexus service beans will be registered with the configured workers if not already registered.

### Referencing Worker names vs Task Queues

Application that incorporates
[Task Queue based Versioning strategy](https://community.temporal.io/t/workflow-versioning-strategies/6911)
may choose to use explicit Worker names to reference because it adds a level of indirection.
This way Task Queue name is specified only once in the config and can be easily changed when needed,
while all the auto-discovery annotations reference the Worker by its static name.

An application whose lifecycle doesn't involve changing task queue names may prefer to reference
Task Queue names directly for simplicity.

Note: Worker whose name is not explicitly specified is named after it's Task Queue.

## Interceptors

To enable interceptors users can create beans implementing the `io.temporal.common.interceptors.WorkflowClientInterceptor`
, `io.temporal.common.interceptors.ScheduleClientInterceptor`, or `io.temporal.common.interceptors.WorkerInterceptor` 
interface. Interceptors will be registered in the order specified by the `@Order` annotation.

## Customization of `*Options`

To provide freedom in customization of `*Options` instances that are created by this module,
beans that implement `io.temporal.spring.boot.TemporalOptionsCustomizer<OptionsBuilderType>`
interface may be added to the Spring context.

Where `OptionsType` may be one of:
- `WorkflowServiceStubsOptions.Builder`
- `WorkflowClientOption.Builder`
- `WorkerFactoryOptions.Builder`
- `WorkerOptions.Builder`
- `WorkflowImplementationOptions.Builder`
- `TestEnvironmentOptions.Builder`

`io.temporal.spring.boot.WorkerOptionsCustomizer` may be used instead of `TemporalOptionsCustomizer<WorkerOptions.Builder>`
if `WorkerOptions` needs to be modified differently depending on the Task Queue or Worker name.

# Integrations

## Metrics

You can set up built-in Spring Boot Metrics using Spring Boot Actuator 
following [one of the manuals](https://tanzu.vmware.com/developer/guides/spring-prometheus/). 
This module will pick up the `MeterRegistry` bean configured this way and use to report Temporal Metrics.

Alternatively, you can define a custom `io.micrometer.core.instrument.MeterRegistry` bean in the application context.

## Tracing

You can set up Spring Cloud Sleuth with OpenTelemetry export 
following [one of the manuals](https://betterprogramming.pub/distributed-tracing-with-opentelemetry-spring-cloud-sleuth-kafka-and-jaeger-939e35f45821).
This module will pick up the `OpenTelemetry` bean configured by `spring-cloud-sleuth-otel-autoconfigure` and use it for Temporal Traces.

Alternatively, you can define a custom 
- `io.opentelemetry.api.OpenTelemetry` for OpenTelemetry or
- `io.opentracing.Tracer` for Opentracing 
bean in the application context.

# Testing

Add the following to your `application.yml` to reconfigure the assembly to work through 
`io.temporal.testing.TestWorkflowEnvironment` that uses in-memory Java Test Server:

```yml
spring.temporal:
  test-server:
    enabled: true
```

When `spring.temporal.test-server.enabled:true` is added, `spring.temporal.connection` section is ignored.
This allows to wire `TestWorkflowEnvironment` bean in your unit tests:

```yml
@SpringBootTest(classes = Test.Configuration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class Test {
  @Autowired ConfigurableApplicationContext applicationContext;
  @Autowired TestWorkflowEnvironment testWorkflowEnvironment;
  @Autowired WorkflowClient workflowClient;

  @BeforeEach
  void setUp() {
    applicationContext.start();
  }

  @Test
  @Timeout(value = 10)
  public void test() {
    # ...
  }

  @ComponentScan # to discover activity beans annotated with @Component
  # @EnableAutoConfiguration # can be used to load only AutoConfigurations if usage of @ComponentScan is not desired 
  public static class Configuration {}
}
```

# Running Multiple Name Space (experimental)

Along with the root namespace, you can configure multiple non-root namespaces in the application.yml file. Different namespaces can have different configurations including but not limited to different connection options, registered workflows/activities, data converters etc.

```yml
spring.temporal:
    namespaces:
      - namespace: assign
        alias: assign
        workers-auto-discovery:
          packages: com.component.temporal.assign
        workers:
          - task-queue: global
      - namespace: unassign
        alias: unassign
        workers-auto-discovery:
          packages: com.component.temporal.unassign
        workers:
          - task-queue: global
```

## Customization

All customization points for the root namespace also exist for the non-root namespaces. To specify for a particular 
namespace users just need to append the alias/namespace to the bean. Currently, auto registered interceptors are not 
supported, but `WorkerFactoryOptions` can always be used to customize it per namespace.

```java
    // TemporalOptionsCustomizer type beans must start with the namespace/alias you defined and end with function class 
    // name you want to customizer and concat Customizer as the bean name.
    @Bean
    TemporalOptionsCustomizer<WorkflowServiceStubsOptions.Builder> assignWorkflowServiceStubsCustomizer() {
        return builder -> builder.setKeepAliveTime(Duration.ofHours(1));
    }

    // Data converter is also supported
    @Bean
    DataConverter assignDataConverter() {
        return DataConverter.getDefaultInstance();
    }
```

## Injecting

If you want to autowire different `WorkflowClient` instances from different namespaces, you can use the `@Resource` 
annotation with the bean name corresponding to the namespace alias + `WorkflowClient`:

```java
    // temporalWorkflowClient is the primary and rootNamespace bean. 
    @Resource
    WorkflowClient workflowClient;

    // Bean name here corresponds to the namespace/alias + Simple Class Name
    @Resource(name = "assignWorkflowClient")
    private WorkflowClient assignWorkflowClient;

    @Resource(name = "unassignWorkflowClient")
    private WorkflowClient unassignWorkflowClient;
```