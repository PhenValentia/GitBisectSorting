package org.rgrig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.net.URI;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

class Client extends WebSocketClient {
    enum State {
        START, IN_PROGRESS,
    }

    State state = State.START;

    String repoName = null;
    Map<String, List<String>> parents;
    String[] commits;
    Map<String, Boolean> good;
    int next;

    String kentId;
    String token;

    Client(final URI server, final String kentId, final String token) {
        super(server);
        this.kentId = kentId;
        this.token = token;
    }

    @Override
    public void onMessage(final String messageText) {
        final JSONObject message = new JSONObject(messageText);
        System.out.printf("received: %s\n", message);
        switch (state) {
        case START:
            if (message.has("Repo")) {
                // Make an array with all commits. Also, remember the repo dag.
                JSONObject jsonRepo = message.getJSONObject("Repo");
                repoName = jsonRepo.getString("name");
                JSONArray jsonDag = jsonRepo.getJSONArray("dag");
                commits = new String[jsonDag.length()];
                parents = new HashMap<>();
                for (int i = 0; i < jsonDag.length(); ++i) {
                    JSONArray entry = jsonDag.getJSONArray(i);
                    commits[i] = entry.getString(0);
                    JSONArray iParents = entry.getJSONArray(1);
                    List<String> ps = new ArrayList<>();
                    for (int j = 0; j < iParents.length(); ++j) {
                        ps.add(iParents.getString(j));
                    }
                    parents.put(commits[i], ps);
                }
                assert commits.length >= 2;
            } else if (message.has("Instance")) {
                if (repoName == null) {
                    System.err.println("Protocol error: instace without having seen a repo.");
                    close();
                }
                JSONObject jsonInstance = message.getJSONObject("Instance");
                String knownGood = jsonInstance.getString("good");
                String knownBad = jsonInstance.getString("bad");
                System.out.printf("Solving instance (good %s; bad %s) of %s\n", knownGood, knownBad, repoName);
                if (commits.length > 30) {
                    send("\"GiveUp\"");
                    return;
                }
                state = State.IN_PROGRESS;
                next = 0;
                good = new HashMap<>();
                ask();
            } else if (message.has("Score")) {
                close();
            } else {
                System.err.println("Unexpected message while waiting for a problem.");
                close();
            }
            break;
        case IN_PROGRESS:
            if (message.has("Answer")) {
                good.put(commits[next++], "Good".equals(message.get("Answer")));
                if (next == commits.length) {
                    for (int j = 0; j < commits.length; ++j) {
                        boolean allParentsGood = true;
                        for (String p : parents.get(commits[j])) {
                            allParentsGood &= good.get(p);
                        }
                        if (!good.get(commits[j]) && allParentsGood) {
                            state = State.START;
                            send(new JSONObject().put("Solution", commits[j]).toString());
                            return;
                        }
                    }
                    assert false; // No BUG?
                } else {
                    ask();
                }
            } else {
                System.err.println("Unexpected message while in-progress.");
                close();
            }
            break;
        default:
            assert false;
        }
    }

    void ask() {
        send(new JSONObject().put("Question", commits[next]).toString());
    }

    @Override
    public void onClose(final int arg0, final String arg1, final boolean arg2) {
        System.out.printf("L: onClose(%d, %s, %b)\n", arg0, arg1, arg2);
    }

    @Override
    public void onError(final Exception arg0) {
        System.out.printf("L: onError(%s)\n", arg0);
    }

    @Override
    public void onOpen(final ServerHandshake hs) {
        JSONArray authorization = new JSONArray(new Object[]{kentId, token});
        send(new JSONObject().put("User", authorization).toString());
    }
}