package models.lobby;

import akka.actor.ActorRef;
import akka.pattern.Patterns;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.concurrent.TimeoutException;
import models.chat.ChatFactory;
import models.factory.Factory;
import play.libs.F;
import play.libs.Json;
import play.mvc.WebSocket;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import utils.gamebus.GameMessages;
import utils.gamebus.GameMessages.Join;


public class LobbyFactory extends Factory {

    public static synchronized void createLobby(final String username, WebSocket.In<JsonNode> in, WebSocket.Out<JsonNode> out) throws Exception {
        int trial = 0;
        
        final ActorRef finalRoom = create("lobby", Lobby.class);
        Future<Object> future = Patterns.ask(finalRoom, new Join(username, out), 50000);
        // Send the Join message to the room
        String result;
        try {
            result = (String) Await.result(future, Duration.create(50, SECONDS));
        }
        catch (TimeoutException timeout) {
            throw new Exception("Lobby creation failed");
        }  
        if ("OK".equals(result)) {
            ChatFactory.createChat(username, "lobby", in, out);
            //Define the actions to be performed on the websockets
            in.onMessage(new F.Callback<JsonNode>() {
                @Override
                public void invoke(JsonNode event) {
                    finalRoom.tell(event, finalRoom);
                }
            });

            // When the socket is closed.
            in.onClose(new F.Callback0() {
                @Override
                public void invoke() {
                     finalRoom.tell(GameMessages.composeQuit(username),finalRoom);
                }
            });
        } else {
            // Cannot connect, create a Json error.
            ObjectNode error = Json.newObject();
            error.put("error", result);
            // Send the error to the socket.
            out.write(error);
        }
    }
    
    
}
