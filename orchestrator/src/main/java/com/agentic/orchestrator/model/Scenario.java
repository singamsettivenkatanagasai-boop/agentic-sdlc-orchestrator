package com.agentic.orchestrator.model;

/** The three scenario types the platform can plan and execute. */
public enum Scenario {
    /** New system built from an empty workspace. */
    GREENFIELD,
    /** Enhancement of an existing codebase (impact analysis, migration, regression). */
    BROWNFIELD,
    /** Vague requirement that must be clarified by a human before it is scoped. */
    AMBIGUOUS
}
