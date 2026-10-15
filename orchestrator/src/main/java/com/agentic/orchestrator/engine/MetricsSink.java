package com.agentic.orchestrator.engine;

/** Where reliability signals are published. The Spring layer adapts this to Micrometer. */
public interface MetricsSink {
    MetricsSink NOOP = new MetricsSink() {
        @Override public void count(String name) { }
        @Override public void timer(String name, long millis) { }
    };

    void count(String name);

    void timer(String name, long millis);
}
