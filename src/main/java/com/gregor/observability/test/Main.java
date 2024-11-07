package com.gregor.observability.test;

import io.opentelemetry.api.trace.*;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.GlobalOpenTelemetry;

public class Main {
    public static void main(String[] args) {
        OpenTelemetry ot = GlobalOpenTelemetry.get();
        Tracer tracer = ot.getTracer("observability-test");
        Span span = tracer.spanBuilder("main").startSpan();
        span.addEvent("This is an event!");
        System.err.println("'sup");
        if (span.isRecording()) {
            System.err.println("trace: " + span.getSpanContext().getTraceId());
        }
        span.setStatus(StatusCode.OK);
    }
}
