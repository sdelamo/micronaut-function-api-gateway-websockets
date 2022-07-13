package example.micronaut;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

import java.util.Collections;

public class Main {
    private final static String ROOT_PACKAGE = "example.micronaut";
    private final static String PROJECT_NAME = "MicronautApigatewayWebsockets";

    public static void main(final String[] args) {
        App app = new App();
        Project project = new Project(PROJECT_NAME,
                Collections.singletonList(new Module("app", ROOT_PACKAGE)));
        new AppStack(project, app,  project.getName()+ "AppStack", StackProps.builder()
                .env(Environment.builder()
                        .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                        .region(System.getenv("CDK_DEFAULT_REGION"))
                        .build())
                .build());
        app.synth();
    }
}