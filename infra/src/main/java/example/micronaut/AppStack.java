package example.micronaut;

import io.micronaut.aws.cdk.function.MicronautFunction;
import io.micronaut.aws.cdk.function.MicronautFunctionFile;
import io.micronaut.starter.application.ApplicationType;
import io.micronaut.starter.options.BuildTool;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apigatewayv2.alpha.WebSocketApi;
import software.amazon.awscdk.services.apigatewayv2.alpha.WebSocketRouteOptions;
import software.amazon.awscdk.services.apigatewayv2.alpha.WebSocketStage;
import software.amazon.awscdk.services.apigatewayv2.integrations.alpha.WebSocketLambdaIntegration;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.GlobalSecondaryIndexProps;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Tracing;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.constructs.Construct;

import java.util.HashMap;
import java.util.Map;

public class AppStack extends Stack {

    protected static final String ATTRIBUTE_PK = "pk";
    protected static final String ATTRIBUTE_SK = "sk";
    protected static final String ATTRIBUTE_GSI_1_PK = "GSI1PK";
    protected static final String ATTRIBUTE_GSI_1_SK = "GSI1SK";
    protected static final String INDEX_GSI_1 = "GSI1";
    private final Project project;

    public AppStack(final Project project, final Construct parent, final String id) {
        this(project, parent, id, null);
    }

    public AppStack(final Project project, final Construct parent, final String id, final StackProps props) {
        super(parent, id, props);
        this.project = project;

        Table table = createTable();
        Map<String, String> environmentVariables = new HashMap<>();
        environmentVariables.put("DYNAMODB_TABLE_NAME", table.getTableName());
        // https://aws.amazon.com/blogs/compute/optimizing-aws-lambda-function-performance-for-java/
        environmentVariables.put("JAVA_TOOL_OPTIONS", "-XX:+TieredCompilation -XX:TieredStopAtLevel=1");

        Module websocketsModule = project.findModuleByName("app");
        Function websocketsFunction = createFunction(environmentVariables,
                websocketsModule.getName(),
                functionHandler(websocketsModule));
        table.grantReadWriteData(websocketsFunction);

        WebSocketApi webSocketApi = createWebSocketApi(project.getName(), websocketsFunction);
        webSocketApi.grantManageConnections(websocketsFunction);
        WebSocketStage stage = createWebSocketStage(project.getName(), webSocketApi);
        stage.grantManagementApiAccess(websocketsFunction);
        output(stage);
    }

    public Table createTable() {
        Table table = Table.Builder.create(this, project.getName() + "-table")
                .partitionKey(Attribute.builder()
                        .name(ATTRIBUTE_PK)
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name(ATTRIBUTE_SK)
                        .type(AttributeType.STRING)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build();
        table.addGlobalSecondaryIndex(globalSecondaryIndexProps(INDEX_GSI_1,
                ATTRIBUTE_GSI_1_PK,
                ATTRIBUTE_GSI_1_SK));
        return table;
    }

    private GlobalSecondaryIndexProps globalSecondaryIndexProps(String indexName,
                                                                String pk,
                                                                String sk) {
        return GlobalSecondaryIndexProps.builder()
                .indexName(indexName)
                .partitionKey(Attribute.builder()
                        .name(pk)
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name(sk)
                        .type(AttributeType.STRING)
                        .build())
                .build();
    }

    private Function createFunction(Map<String, String> environmentVariables,
                                    String moduleName,
                                    String handler) {
        return createFunction(environmentVariables, ApplicationType.FUNCTION, moduleName, handler);
    }

    private Function createFunction(Map<String, String> environmentVariables,
                                    ApplicationType applicationType,
                                    String moduleName,
                                    String handler) {
        Function.Builder builder =  MicronautFunction.create(applicationType,
                        false,
                        this,
                        project.getName() + moduleName + "-java-function")
                .environment(environmentVariables)
                .code(Code.fromAsset(functionPath(moduleName)))
                .timeout(Duration.seconds(20))
                .memorySize(1024)
                .tracing(Tracing.ACTIVE)
                .logRetention(RetentionDays.FIVE_DAYS);

        return (handler != null) ? builder.handler(handler).build() : builder.build();
    }

    private WebSocketApi createWebSocketApi(String projectName, Function function) {
        return WebSocketApi.Builder.create(this, projectName + "-function-api-websocket")
                .defaultRouteOptions(WebSocketRouteOptions.builder()
                        .integration((new WebSocketLambdaIntegration("default-route-integration", function)))
                        .build())
                .connectRouteOptions(WebSocketRouteOptions.builder()
                        .integration((new WebSocketLambdaIntegration("connect-route-integration", function)))
                        .build())
                .disconnectRouteOptions(WebSocketRouteOptions.builder()
                        .integration((new WebSocketLambdaIntegration("disconnect-route-integration", function)))
                        .build())
                .build();
    }

    private WebSocketStage createWebSocketStage(String projectName, WebSocketApi webSocketApi) {
        return WebSocketStage.Builder.create(this, projectName + "-function-api-websocket-stage-production")
                .webSocketApi(webSocketApi)
                .stageName("production")
                .autoDeploy(true)
                .build();
    }

    private String functionHandler(Module module) {
        return module.getPackageName() + ".FunctionRequestHandler";
    }

    private static String functionPath(String moduleName) {
        return "../" + moduleName + "/build/libs/" + functionFilename(moduleName);
    }

    private static String functionFilename(String moduleName) {
        return MicronautFunctionFile.builder()
                .graalVMNative(false)
                .version("0.1")
                .archiveBaseName(moduleName)
                .buildTool(BuildTool.GRADLE)
                .build();
    }

    private void output(WebSocketStage stage) {
        CfnOutput.Builder.create(this, "WebSocketUrl")
                .exportName("WebSocketUrl")
                .value(stage.getUrl())
                .build();
    }
}