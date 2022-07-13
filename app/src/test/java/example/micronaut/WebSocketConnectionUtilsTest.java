package example.micronaut;

import example.micronaut.WebSocketConnection;
import example.micronaut.WebSocketConnectionUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketConnectionUtilsTest {

    @Test
    void testUriBuilder() {
        WebSocketConnection webSocketConnection = new WebSocketConnection("us-east-1",
                "ydvi4h9bvd",
                "production",
                "x94eGsoAMCLig=",
                "ydvi4h9bvd.execute-api.us-east-1.amazonaws.com");
        assertEquals(WebSocketConnectionUtils.uriOf(webSocketConnection).toString(),
                "https://" + webSocketConnection.getDomainName() + "/production");
    }
}