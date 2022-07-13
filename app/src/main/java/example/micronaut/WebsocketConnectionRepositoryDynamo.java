package example.micronaut;

import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class WebsocketConnectionRepositoryDynamo implements WebsocketConnectionRepository {
    private static final String HASH = "#";
    private static final String ATTRIBUTE_PK = "pk";
    private static final String ATTRIBUTE_SK = "sk";
    private static final String ATTRIBUTE_GSI_1_PK = "GSI1PK";
    private static final String ATTRIBUTE_GSI_1_SK = "GSI1SK";
    private static final String INDEX_GSI_1 = "GSI1";
    private static final String ATTRIBUTE_CONNECTION_ID = "connectionId";
    private static final String ATTRIBUTE_STAGE = "stage";
    private static final String ATTRIBUTE_API_ID = "apiId";
    private static final String ATTRIBUTE_DOMAIN_NAME = "domainName";
    public static final String ATTRIBUTE_REGION = "region";
    private final DynamoDbClient dynamoDbClient;
    private final DynamoConfiguration dynamoConfiguration;

    public WebsocketConnectionRepositoryDynamo(DynamoDbClient dynamoDbClient,
                                               DynamoConfiguration dynamoConfiguration) {
        this.dynamoDbClient = dynamoDbClient;
        this.dynamoConfiguration = dynamoConfiguration;
    }

    @Override
    public void save(@NonNull @NotNull @Valid WebSocketConnection websocketConnection) {
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(dynamoConfiguration.getTableName())
                .item(item(websocketConnection))
                .build());
    }

    @Override
    public void delete(@NonNull @NotNull @Valid WebSocketConnection websocketConnection) {
        dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                        .tableName(dynamoConfiguration.getTableName())
                        .key(keyForItem(websocketConnection))
                .build());
    }

    private static Map<String, AttributeValue> keyForItem(WebSocketConnection websocketConnection) {
        Map<String, AttributeValue> item = new HashMap<>();
        AttributeValue pk = s(prefix(websocketConnection.getClass()) + websocketConnection.getConnectionId() +
                HASH + websocketConnection.getApiId() + HASH + websocketConnection.getStage());
        item.put(ATTRIBUTE_PK, pk);
        item.put(ATTRIBUTE_SK, pk);
        return item;
    }

    private static String prefix(@NonNull Class<?> cls) {
        return cls.getSimpleName().toUpperCase() + HASH;
    }

    @NonNull
    private static AttributeValue classAttributeValue(@NonNull Class<?> cls) {
        return AttributeValue.builder()
                .s(cls.getSimpleName())
                .build();
    }

    @NonNull
    private static Map<String, AttributeValue> item(@NonNull WebSocketConnection websocketConnection) {
        Map<String, AttributeValue> result = new HashMap<>();
        result.putAll(keyForItem(websocketConnection));
        result.put(ATTRIBUTE_GSI_1_PK, classAttributeValue(websocketConnection.getClass()));
        result.put(ATTRIBUTE_GSI_1_SK, result.get(ATTRIBUTE_PK));
        result.put(ATTRIBUTE_REGION, s(websocketConnection.getRegion()));
        result.put(ATTRIBUTE_CONNECTION_ID, s(websocketConnection.getConnectionId()));
        result.put(ATTRIBUTE_STAGE, s(websocketConnection.getStage()));
        result.put(ATTRIBUTE_API_ID, s(websocketConnection.getApiId()));
        if (websocketConnection.getDomainName() != null) {
            result.put(ATTRIBUTE_DOMAIN_NAME, s(websocketConnection.getDomainName()));
        }
        return  result;
    }

    @NonNull
    private static AttributeValue s(String str) {
        return AttributeValue.builder().s(str).build();
    }

}
