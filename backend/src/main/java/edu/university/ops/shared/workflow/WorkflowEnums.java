package edu.university.ops.shared.workflow;

/** Status and decision vocabularies of the workflow engine (AGENT.md §16). */
public final class WorkflowEnums {

    private WorkflowEnums() {
    }

    public enum InstanceStatus { RUNNING, APPROVED, REJECTED, RETURNED, CANCELLED }

    public enum StepStatus { PENDING, ACTIVE, COMPLETED, SKIPPED, CANCELLED }

    public enum TaskStatus { OPEN, COMPLETED, CANCELLED }

    public enum Decision { APPROVE, REJECT, RETURN_FOR_CORRECTION, FORWARD }
}
