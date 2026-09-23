package com.hr.recruitment.local;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.hr.recruitment.common.AppContext;
import com.hr.recruitment.common.DataSourceFactory;
import io.javalin.Javalin;

import java.util.UUID;

/**
 * Runs the API as a plain HTTP server, without AWS, for local development.
 * Maps each Javalin request onto an APIGatewayV2HTTPEvent so it goes through the
 * exact same Router the Lambda handler uses.
 *
 * Env vars: DB_HOST, DB_PORT (default 5432), DB_NAME, DB_USER (default postgres), DB_PASSWORD.
 * Run: mvn -q package -DskipTests && java -cp target/api.jar com.hr.recruitment.local.LocalServer
 */
public final class LocalServer {

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        // Local dev only. In AWS, CORS on the Function URL is set by Terraform
        // (infrastructure.spec.md: origin = the S3 website URL), not by this class.
        String allowedOrigin = System.getenv().getOrDefault("ALLOWED_ORIGIN", "http://localhost:4200");
        AppContext ctx = AppContext.createWithDataSource(DataSourceFactory.create());

        Javalin app = Javalin.create();
        app.before(javalinCtx -> {
            javalinCtx.header("Access-Control-Allow-Origin", allowedOrigin);
            javalinCtx.header("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
            javalinCtx.header("Access-Control-Allow-Headers", "Content-Type");
        });
        app.options("/*", javalinCtx -> javalinCtx.status(204));
        app.get("/api/v1/candidates/{id}", ctx2 -> handle(ctx2, ctx));
        app.post("/api/v1/candidates", ctx2 -> handle(ctx2, ctx));
        app.put("/api/v1/candidates/{id}", ctx2 -> handle(ctx2, ctx));

        app.start(port);
        System.out.println("Local API listening on http://localhost:" + port);
    }

    private static void handle(io.javalin.http.Context javalinCtx, AppContext appCtx) {
        APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
        event.setRawPath(javalinCtx.path());
        event.setBody(javalinCtx.body());

        APIGatewayV2HTTPEvent.RequestContext requestContext = new APIGatewayV2HTTPEvent.RequestContext();
        APIGatewayV2HTTPEvent.RequestContext.Http http = new APIGatewayV2HTTPEvent.RequestContext.Http();
        http.setMethod(javalinCtx.method().name());
        http.setPath(javalinCtx.path());
        requestContext.setHttp(http);
        event.setRequestContext(requestContext);

        APIGatewayV2HTTPResponse response = appCtx.router().dispatch(event, UUID.randomUUID().toString());

        javalinCtx.status(response.getStatusCode());
        if (response.getHeaders() != null) {
            response.getHeaders().forEach(javalinCtx::header);
        }
        javalinCtx.result(response.getBody() == null ? "" : response.getBody());
    }
}
