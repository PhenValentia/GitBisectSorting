package org.rgrig;

import java.net.URI;
import java.net.URISyntaxException;

import org.java_websocket.client.WebSocketClient;

public class App {
    public static void main(final String[] args) throws URISyntaxException {
        if (args.length != 3) {
            System.err.println("usage: <cmd> <server-ip> <kent-id> <access-token>");
            return;
        }
        String uri = String.format("ws://%s:1234", args[0]);
        String id = args[1];
        String token = args[2];
        WebSocketClient client = new Client(new URI(uri), id, token);
        client.connect();
    }
}
