package example.micronaut;

import io.micronaut.aws.cdk.function.MicronautFunctionFile;
import io.micronaut.starter.options.BuildTool;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.certificatemanager.Certificate;
import software.amazon.awscdk.services.certificatemanager.CertificateValidation;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.elasticloadbalancingv2.AddApplicationTargetGroupsProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationListener;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationLoadBalancer;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationProtocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.ApplicationTargetGroup;
import software.amazon.awscdk.services.elasticloadbalancingv2.BaseApplicationListenerProps;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.elasticloadbalancingv2.ListenerCertificate;
import software.amazon.awscdk.services.elasticloadbalancingv2.Protocol;
import software.amazon.awscdk.services.elasticloadbalancingv2.TargetType;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.HostedZoneProviderProps;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.LoadBalancerTarget;
import software.constructs.Construct;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ContainerAppStack extends Stack {

    public ContainerAppStack(final Construct parent, final String id) {
        this(parent, id, null);
    }

    public ContainerAppStack(final Construct parent, final String id, final StackProps props) {
        super(parent, id, props);

        String projectName = "cfptracker";
        String projectDomain = "cfptracker.com";
        String environment = "production";
        String projectPrefix = projectName + "-" + environment + "-server";

        Map<String, String> environmentVariables = new HashMap<>();

        Table table = Table.Builder.create(this, projectPrefix + "-table")
                .partitionKey(Attribute.builder()
                        .name("pk")
                        .type(AttributeType.STRING)
                        .build())
                .sortKey(Attribute.builder()
                        .name("sk")
                        .type(AttributeType.STRING)
                        .build())
                .build();
        environmentVariables.put("DYNAMODB_TABLE_NAME", table.getTableName());

        Vpc vpc = Vpc.Builder.create(this, projectPrefix + "-vpc")
                .maxAzs(2)
                .build();

        Repository repository = Repository.Builder.create(this, projectPrefix + "-repository")
                .build();

        Cluster cluster = Cluster.Builder.create(this, projectPrefix + "-cluster")
                .vpc(vpc)
                .build();

        ApplicationLoadBalancer elb = ApplicationLoadBalancer.Builder.create(this, projectPrefix + "-elb")
                .vpc(vpc)
                .vpcSubnets(SubnetSelection.builder()
                        .subnets(vpc.getPublicSubnets())
                        .build())
                .internetFacing(true)
                .build();
        IHostedZone zone = HostedZone.fromLookup(this, projectPrefix + "-zone",
                        HostedZoneProviderProps.builder()
                                .domainName(projectDomain)
                                .build());

        ARecord.Builder.create(this, projectPrefix + "-domain")
                .recordName(((!environment.equals("production") ? (environment +"-") : "") + "api." + projectDomain))
                .target(RecordTarget.fromAlias(new LoadBalancerTarget(elb)))
                .ttl(Duration.seconds(300))
                .comment(environment + " API domain")
                .zone(zone)
                .build();

        ApplicationTargetGroup targetGroupHttp = ApplicationTargetGroup.Builder.create(this, projectPrefix + "-target-group")
                .port(80)
                .vpc(vpc)
                .protocol(ApplicationProtocol.HTTP)
                .targetType(TargetType.IP)
                .build();

        targetGroupHttp.configureHealthCheck(HealthCheck.builder()
                .healthyHttpCodes("200")
                .path("/health")
                .port("8080")
                .protocol(Protocol.HTTP)
                .build());

        Certificate cert = Certificate.Builder.create(this, projectPrefix + "-certificate")
                .domainName("*." + projectDomain)
                .validation(CertificateValidation.fromDns(zone))
                .build();

        ApplicationListener listener = elb.addListener( projectPrefix + "-elb-listener", BaseApplicationListenerProps.builder()
                .open(true)
                .port(443)
                .certificates(Collections.singletonList(ListenerCertificate.fromArn(cert.getCertificateArn())))
                .build());

        //CfnWebACL cloudFrontWaf = CfnWebACL.Builder.create(this, projectPrefix + "cf-waf")
        //        .build();

        listener.addTargetGroups(projectPrefix + "-elb-listener-target-group", AddApplicationTargetGroupsProps.builder()
                .targetGroups(Collections.singletonList(targetGroupHttp))
                .build());

        SecurityGroup elbSG = SecurityGroup.Builder.create(this, projectPrefix + "-elb-sg")
                .vpc(vpc)
                .allowAllOutbound(true)
                .build();
        elb.addSecurityGroup(elbSG);
/*
        Bucket assetsBucket = Bucket.Builder.create(this, projectPrefix + "-s3-bucket-assets")
                .build();
        environmentVariables.put("ASSETS_BUCKET_NAME", assetsBucket.getBucketName());

        Role taskRole = Role.Builder.create(this, projectPrefix + "-task-role")
                .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                .roleName(projectPrefix + "-task-role")
                .description("Role that the api task definitions use to run the api code")
                .build();
        PolicyStatement assetsBucketAccessPolicyStatement = PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Collections.singletonList("S3:*"))
                .resources(Collections.singletonList(assetsBucket.getBucketArn()))
                .build();
        PolicyStatement tableAccessPolicyStatement = PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Collections.singletonList("dynamodb:*"))
                .resources(Collections.singletonList(table.getTableArn()))
                .build();
        taskRole.attachInlinePolicy(Policy.Builder.create(this, projectPrefix  + "-task-policy")
                .statements(Arrays.asList(assetsBucketAccessPolicyStatement, tableAccessPolicyStatement))
                .build());
        TaskDefinition taskDefinition = TaskDefinition.Builder.create(this, projectPrefix + "-task")
                .family("task")
                .compatibility(Compatibility.EC2_AND_FARGATE)
                .cpu("256")
                .memoryMiB("512")
                .networkMode(NetworkMode.AWS_VPC)
                .taskRole(taskRole)
                .build();
        EcrImage image = ContainerImage.fromEcrRepository(repository, "latest");
        LogDriver logDriver = LogDriver.awsLogs(AwsLogDriverProps.builder()
                .streamPrefix(projectPrefix)
                .build());
        ContainerDefinition container = taskDefinition.addContainer(PREFIX + "-container", ContainerDefinitionOptions.builder()
                        .containerName(PREFIX + "-container")
                .image(image)
                .memoryLimitMiB(512)
                .environment(environmentVariables)
                .logging(logDriver)
                .build());
        container.addPortMappings(PortMapping.builder()
                .containerPort(8080).build());

        SecurityGroup ecsSG = SecurityGroup.Builder.create(this, projectPrefix + "-ecs-sg")
                .vpc(vpc)
                .allowAllOutbound(true)
                .build();
        ecsSG.getConnections().allowFrom(elbSG, Port.allTcp(), "Application Load balancer");

        FargateService service = FargateService.Builder.create(this, projectPrefix + "-service")
                .cluster(cluster)
                .desiredCount(1)
                .taskDefinition(taskDefinition)
                .securityGroups(Collections.singletonList(ecsSG))
                .assignPublicIp(true)
                .build();
        ScalableTaskCount scalableTarget = service.autoScaleTaskCount(EnableScalingProps.builder()
                .minCapacity(1)
                .maxCapacity(4)
                .build());
        scalableTarget.scaleOnMemoryUtilization(projectPrefix + "-ScaleUpMem",
                MemoryUtilizationScalingProps.builder()
                        .targetUtilizationPercent(75)
                        .build());
        scalableTarget.scaleOnCpuUtilization(projectPrefix + "-ScaleUpCPU",
                CpuUtilizationScalingProps.builder()
                        .targetUtilizationPercent(75)
                        .build());

        CfnOutput.Builder.create(this, environment + "ServiceName")
                .exportName(environment + "ServiceName")
                .value(service.getServiceName());
        CfnOutput.Builder.create(this, environment + "ImageName")
                .exportName(environment + "ImageName")
                .value(image.getImageName());
*/
        CfnOutput.Builder.create(this, environment + "ImageRepositoryUri")
                .exportName(environment + "ImageRepositoryUri")
                .value(repository.getRepositoryUri());

        /*
        CfnOutput.Builder.create(this, environment + "ClusterName")
                .exportName(environment + "ClusterName")
                .value(cluster.getClusterName());

         */
    }

    public static String functionPath() {
        return "../app/build/libs/" + functionFilename();
    }

    public static String functionFilename() {
        return MicronautFunctionFile.builder()
            .graalVMNative(false)
            .version("0.1")
            .archiveBaseName("app")
            .buildTool(BuildTool.GRADLE)
            .build();
    }
}