package io.temporal.internal.statemachines;

import io.temporal.api.command.v1.Command;
import io.temporal.api.command.v1.UpsertWorkflowSearchAttributesCommandAttributes;
import io.temporal.api.common.v1.SearchAttributes;
import io.temporal.api.enums.v1.CommandType;
import io.temporal.api.enums.v1.EventType;
import io.temporal.workflow.Functions;

final class UpsertSearchAttributesStateMachine
    extends EntityStateMachineInitialCommand<
        UpsertSearchAttributesStateMachine.State,
        UpsertSearchAttributesStateMachine.ExplicitEvent,
        UpsertSearchAttributesStateMachine> {

  private SearchAttributes searchAttributes;

  public static void newInstance(
      SearchAttributes searchAttributes,
      Functions.Proc1<CancellableCommand> commandSink,
      Functions.Proc1<StateMachine> stateMachineSink) {
    new UpsertSearchAttributesStateMachine(searchAttributes, commandSink, stateMachineSink);
  }

  private UpsertSearchAttributesStateMachine(
      SearchAttributes searchAttributes,
      Functions.Proc1<CancellableCommand> commandSink,
      Functions.Proc1<StateMachine> stateMachineSink) {
    super(STATE_MACHINE_DEFINITION, commandSink, stateMachineSink);
    this.searchAttributes = searchAttributes;
    explicitEvent(ExplicitEvent.SCHEDULE);
  }

  enum ExplicitEvent {
    SCHEDULE
  }

  enum State {
    CREATED,
    UPSERT_COMMAND_CREATED,
    UPSERT_COMMAND_RECORDED,
  }

  public static final StateMachineDefinition<
          State, ExplicitEvent, UpsertSearchAttributesStateMachine>
      STATE_MACHINE_DEFINITION =
          StateMachineDefinition
              .<State, ExplicitEvent, UpsertSearchAttributesStateMachine>newInstance(
                  "UpsertSearchAttributes", State.CREATED, State.UPSERT_COMMAND_RECORDED)
              .add(
                  State.CREATED,
                  ExplicitEvent.SCHEDULE,
                  State.UPSERT_COMMAND_CREATED,
                  UpsertSearchAttributesStateMachine::createUpsertCommand)
              .add(
                  State.UPSERT_COMMAND_CREATED,
                  CommandType.COMMAND_TYPE_UPSERT_WORKFLOW_SEARCH_ATTRIBUTES,
                  State.UPSERT_COMMAND_CREATED)
              .add(
                  State.UPSERT_COMMAND_CREATED,
                  EventType.EVENT_TYPE_UPSERT_WORKFLOW_SEARCH_ATTRIBUTES,
                  State.UPSERT_COMMAND_RECORDED);

  private void createUpsertCommand() {
    addCommand(
        Command.newBuilder()
            .setCommandType(CommandType.COMMAND_TYPE_UPSERT_WORKFLOW_SEARCH_ATTRIBUTES)
            .setUpsertWorkflowSearchAttributesCommandAttributes(
                UpsertWorkflowSearchAttributesCommandAttributes.newBuilder()
                    .setSearchAttributes(searchAttributes)
                    .build())
            .build());
    searchAttributes = null;
  }
}
