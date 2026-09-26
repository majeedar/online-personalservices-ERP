package edu.university.ops.shared.workflow;

/** Kinds of approval: used by approval relations, workflow steps and delegations (AGENT.md §9, §18). */
public enum ApprovalType {
    ABSENCE,
    TRAVEL,
    TIME_CORRECTION,
    FINANCIAL,
    HR_REVIEW
}
