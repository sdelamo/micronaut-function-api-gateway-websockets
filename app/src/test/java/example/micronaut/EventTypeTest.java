package example.micronaut;

import example.micronaut.EventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventTypeTest {

    @Test
    void toStringEventType() {
        assertEquals("DISCONNECT", EventType.DISCONNECT.toString());
        assertEquals("CONNECT", EventType.CONNECT.toString());
    }
}