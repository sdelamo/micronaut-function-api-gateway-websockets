package example.micronaut;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import io.micronaut.function.aws.MicronautRequestHandler;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FunctionRequestHandler extends MicronautRequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

    @Inject
    ApiGatewayManagementApiClientSender apiGatewayManagementApiClientSender;

    @Inject
    WebsocketConnectionRepository websocketConnectionRepository;

    private static final Logger LOG = LoggerFactory.getLogger(FunctionRequestHandler.class);

    @Override
    public APIGatewayV2WebSocketResponse execute(APIGatewayV2WebSocketEvent input) {
        LOG.info("input {}", input);
        EventType.of(input.getRequestContext().getEventType()).ifPresent(eventType -> {
            switch (eventType) {
                case CONNECT:
                    websocketConnectionRepository.save(connectionOfInput(input));
                    break;
                case DISCONNECT:
                    websocketConnectionRepository.delete(connectionOfInput(input));
                    break;
                case MESSAGE:
                    apiGatewayManagementApiClientSender.send(messageOfInput(input));
            }
        });

        APIGatewayV2WebSocketResponse response = new APIGatewayV2WebSocketResponse();
        response.setStatusCode(200);
        return response;
    }

    private WebSocketConnection connectionOfInput(APIGatewayV2WebSocketEvent input) {
        return new WebSocketConnection(System.getenv("AWS_REGION"),
                input.getRequestContext().getApiId(),
                input.getRequestContext().getStage(),
                input.getRequestContext().getConnectionId(),
                input.getRequestContext().getDomainName());
    }

    private WebSocketMessage messageOfInput(APIGatewayV2WebSocketEvent input) {
        return new WebSocketMessage(connectionOfInput(input),
                "You said " + input.getBody());
    }
}

