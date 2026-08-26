package com.ibatulanand.apigateway;

import java.util.Locale;
import java.util.Set;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.smallrye.stork.Stork;
import io.smallrye.stork.api.ServiceInstance;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.HttpRequest;
import io.vertx.mutiny.ext.web.client.HttpResponse;
import io.vertx.mutiny.ext.web.client.WebClient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class GatewayRoutes {

    private static final Logger LOG = Logger.getLogger(GatewayRoutes.class);
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade");

    @Inject
    Vertx vertx;

    @ConfigProperty(name = "gateway.consul-ui.url")
    String consulUiUrl;

    void registerRoutes(@Observes Router router) {
        WebClient client = WebClient.create(vertx);
        router.route("/api/product").handler(BodyHandler.create())
                .handler(context -> proxyService(context, client, "product-service"));
        router.route("/api/order").handler(BodyHandler.create())
                .handler(context -> proxyService(context, client, "order-service"));
        router.routeWithRegex("^/eureka(?:/.*)?$").handler(BodyHandler.create())
                .handler(context -> proxyConsulUi(context, client));
    }

    private void proxyService(RoutingContext context, WebClient client, String serviceName) {
        Context traceContext = Context.current();
        resolveAndForward(context, client, serviceName, context.getBody(), traceContext);
    }

    private void resolveAndForward(RoutingContext context, WebClient client, String serviceName,
            io.vertx.core.buffer.Buffer body, Context parentContext) {
        try {
            Stork.getInstance().getService(serviceName).selectInstance()
                    .subscribe().with(instance -> sendToService(
                                    context, client, serviceName, instance, body, parentContext),
                            failure -> badGateway(context, serviceName, failure));
        } catch (Exception failure) {
            badGateway(context, serviceName, failure);
        }
    }

    private void sendToService(RoutingContext context, WebClient client, String serviceName,
            ServiceInstance instance, io.vertx.core.buffer.Buffer body, Context parentContext) {
        String path = withQuery(context.request().path(), context.request().query());
        String targetPath = joinPath(instance.getPath().orElse(""), path);
        String scheme = instance.isSecure() ? "https" : "http";
        String target = scheme + "://" + instance.getHost() + ":" + instance.getPort() + targetPath;
        HttpRequest<io.vertx.mutiny.core.buffer.Buffer> outbound =
                client.requestAbs(context.request().method(), target);
        copyRequestHeaders(context.request().headers(), outbound);
        Tracer tracer = GlobalOpenTelemetry.getTracer(GatewayRoutes.class.getName());
        Span clientSpan = tracer.spanBuilder(context.request().method().name() + " " + path)
                .setParent(parentContext)
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();
        injectTraceContext(outbound, parentContext.with(clientSpan));
        send(outbound, body)
                .subscribe().with(upstream -> {
                    clientSpan.end();
                    writeResponse(context.response(), upstream);
                }, failure -> {
                    clientSpan.recordException(failure);
                    clientSpan.end();
                    badGateway(context, serviceName, failure);
                });
    }

    private void proxyConsulUi(RoutingContext context, WebClient client) {
        Context traceContext = Context.current();
        String requestPath = context.request().path();
        String suffix = requestPath.equals("/eureka") || requestPath.equals("/eureka/web")
                ? "/ui/"
                : "/ui/" + requestPath.substring("/eureka/".length());
        String target = withQuery(trimTrailingSlash(consulUiUrl) + suffix, context.request().query());
        HttpRequest<io.vertx.mutiny.core.buffer.Buffer> outbound =
                client.requestAbs(context.request().method(), target);
        copyRequestHeaders(context.request().headers(), outbound);
        Tracer tracer = GlobalOpenTelemetry.getTracer(GatewayRoutes.class.getName());
        Span clientSpan = tracer.spanBuilder(context.request().method().name() + " " + requestPath)
                .setParent(traceContext)
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();
        injectTraceContext(outbound, traceContext.with(clientSpan));
        send(outbound, context.getBody())
                .subscribe().with(upstream -> {
                    clientSpan.end();
                    writeResponse(context.response(), upstream);
                }, failure -> {
                    clientSpan.recordException(failure);
                    clientSpan.end();
                    badGateway(context, "consul-ui", failure);
                });
    }

    private static io.smallrye.mutiny.Uni<HttpResponse<io.vertx.mutiny.core.buffer.Buffer>> send(
            HttpRequest<io.vertx.mutiny.core.buffer.Buffer> request, io.vertx.core.buffer.Buffer body) {
        return body == null || body.length() == 0
                ? request.send()
                : request.sendBuffer(new io.vertx.mutiny.core.buffer.Buffer(body));
    }

    private static void injectTraceContext(HttpRequest<?> request, Context context) {
        @SuppressWarnings("unchecked")
        HttpRequest<Object> carrier = (HttpRequest<Object>) request;
        GlobalOpenTelemetry.getPropagators().getTextMapPropagator().inject(context, carrier,
                (TextMapSetter<HttpRequest<Object>>) (target, key, value) -> target.putHeader(key, value));
    }

    private static void copyRequestHeaders(MultiMap source, HttpRequest<?> target) {
        source.forEach(entry -> {
            String name = entry.getKey();
            String lowerName = name.toLowerCase(Locale.ROOT);
            if (!HOP_BY_HOP_HEADERS.contains(lowerName)
                    && !lowerName.equals("host")
                    && !lowerName.equals("content-length")
                    && !lowerName.equals("traceparent")
                    && !lowerName.equals("tracestate")
                    && !lowerName.equals("baggage")) {
                target.getDelegate().headers().add(name, entry.getValue());
            }
        });
    }

    private static void writeResponse(HttpServerResponse response,
            HttpResponse<io.vertx.mutiny.core.buffer.Buffer> upstream) {
        upstream.headers().forEach(entry -> {
            if (!HOP_BY_HOP_HEADERS.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                response.headers().add(entry.getKey(), entry.getValue());
            }
        });
        response.setStatusCode(upstream.statusCode());
        io.vertx.mutiny.core.buffer.Buffer body = upstream.bodyAsBuffer();
        if (body == null) {
            response.end();
        } else {
            response.end(body.getDelegate());
        }
    }

    private static void badGateway(RoutingContext context, String target, Throwable failure) {
        LOG.warnf(failure, "Unable to proxy request to %s", target);
        if (!context.response().ended()) {
            context.response().setStatusCode(502).end("Bad Gateway");
        }
    }

    private static String withQuery(String path, String query) {
        return query == null || query.isEmpty() ? path : path + "?" + query;
    }

    private static String joinPath(String prefix, String path) {
        if (prefix == null || prefix.isEmpty()) {
            return path;
        }
        return trimTrailingSlash(prefix) + "/" + (path.startsWith("/") ? path.substring(1) : path);
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
