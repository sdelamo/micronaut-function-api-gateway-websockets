package example.micronaut;

import io.micronaut.core.annotation.NonNull;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public interface WebsocketConnectionRepository {
    void save(@NonNull @NotNull @Valid WebSocketConnection websocketConnection);

    void delete(@NonNull @NotNull @Valid WebSocketConnection websocketConnection);
}
