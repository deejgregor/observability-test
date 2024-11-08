package com.gregor.observability.test;

import com.github.rvesse.airline.*;
import com.github.rvesse.airline.annotations.*;
import com.github.rvesse.airline.help.*;
import datadog.trace.api.*;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.*;
import io.opentelemetry.instrumentation.annotations.WithSpan;

import io.opentracing.util.GlobalTracer;

import java.io.*;
import java.net.*;

@Command(name = "observability-test", description = "Test sending traces, etc..", defaultCommand = Help.class)
public class Main {
    public static boolean haveDatadogCorrelationIdentifier() {
        try {
            CorrelationIdentifier.getTraceId();
            return true;
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    final static boolean haveDatadogCorrelationIdentifier = haveDatadogCorrelationIdentifier();

    @Option(name = { "--url", "-u" }, description = "URL to test, empty to disable test")
    private String url = "http://localhost:8080/";

    public static void main(String[] args) throws IOException {
        SingleCommand<Main> parser = SingleCommand.singleCommand(Main.class);
        Main main = parser.parse(args);

        OpenTelemetry ot = GlobalOpenTelemetry.get();
        Tracer tracer = ot.getTracer("observability-test");

        Span span = tracer.spanBuilder("main")
                .setAttribute("api", "otel")
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            System.err.println("hello!");

            testExplicitOtelSpan(tracer);
            testAnnotationOtelSpan();
            testExplicitOpentracingSpan();

            try {
                testGet(main.url);
            } catch (IOException ignore) {
                // do nothing -- we don't care about the actual GET, we just want to see the instrumentation span
            }
        } finally {
            span.end();
        }
    }

    private static void testGet(String url) throws IOException {
        URL u = new URL(url);

        HttpURLConnection con = (HttpURLConnection) u.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", "observability-test");

        InputStream is = con.getInputStream();
        byte[] discardBuffer = new byte[1024];
        while (is.available() > 0) {
            is.read(discardBuffer);
        }
        is.close();
    }

    private static void testExplicitOpentracingSpan() {
        io.opentracing.Tracer tracer = GlobalTracer.get();

        io.opentracing.Span span = tracer.buildSpan("testExplicitOpentracingSpan").start();
        try (io.opentracing.Scope ignored = tracer.scopeManager().activate(span)) {
            span.setTag("api", "opentracing");
            String traceId = span.context().toTraceId();
            if (!"".equals(traceId)) {
                System.err.println("opentracing trace ID: " + traceId);
                System.err.println("opentracing trace ID (hex): " + String.format("%016x", Long.parseLong(traceId)));
            }
            if (haveDatadogCorrelationIdentifier) {
                System.err.println("Datadog trace ID: " + CorrelationIdentifier.getTraceId());
            }
        } finally {
            span.finish();
        }
    }

    @WithSpan
    private static void testAnnotationOtelSpan() {
        Span.current().setAttribute("api", "otel");
    }

    private static void testExplicitOtelSpan(Tracer tracer) {
        Span span = tracer.spanBuilder("testExplicitOtelSpan")
                .setAttribute("api", "otel")
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            span.addEvent("This is an event!");
            if (span.isRecording()) {
                System.err.println("otel trace: " + span.getSpanContext().getTraceId());
            }
            span.setStatus(StatusCode.OK);
        } finally {
            span.end();
        }
    }
}
