package com.ourgame.ourgameserver.ws.sockets;

import com.ourgame.ourgameserver.game.Player;
import com.ourgame.ourgameserver.game.Lobby;
import com.ourgame.ourgameserver.game.PlayerService;
import com.ourgame.ourgameserver.game.pack.Atom;
import com.ourgame.ourgameserver.ws.dto.AtomDto;
import com.ourgame.ourgameserver.ws.dto.GameMapDto;
import com.ourgame.ourgameserver.ws.dto.LobbyDto;
import io.socket.socketio.server.SocketIoNamespace;
import io.socket.socketio.server.SocketIoSocket;
import org.json.JSONObject;

import javax.xml.bind.JAXBException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;


public class GameHandlerSocket {
    private final SocketServer socketServer;
    private final Lobby lobby;
    private final SocketIoNamespace namespace;
    private final PlayerService playerService;
    private final Timer timer;

    public GameHandlerSocket(SocketServer socketServer, Lobby lobby, SocketIoNamespace namespace, PlayerService playerService) {
        this.socketServer = socketServer;
        this.lobby = lobby;
        this.namespace = namespace;
        this.playerService = playerService;
        this.timer = new Timer();
        socketSetup();
    }

    public void socketSetup() {
        namespace.on("connect", args -> {
            SocketIoSocket socket = (SocketIoSocket) args[0];
            lobby.addPlayer(getPlayer(socket));
            namespace.broadcast(null, "lobby", new LobbyDto(lobby).toJson());

            disconnectListener(socket);
            readyListener(socket);
            startGame(socket);
        });
    }

    private void disconnectListener(SocketIoSocket socket) {
        socket.on("disconnect", args -> {
            lobby.removePlayer(getPlayer(socket));
            namespace.broadcast(null, "lobby", new LobbyDto(lobby).toJson());
        });
    }

    private void readyListener(SocketIoSocket socket) {
        socket.on("ready", args -> {
            JSONObject response = (JSONObject) args[0];
            lobby.setPlayerReadyStatus(getPlayer(socket), response.getBoolean("ready"));
            namespace.broadcast(null, "lobby", new LobbyDto(lobby).toJson());
        });
    }

    private void startGame(SocketIoSocket socket) {
        socket.on("start", args -> {
            try {
                GameMapDto map = new GameMapDto(lobby.getPack());
                if (lobby.getHost().equals(getPlayer(socket)) && lobby.arePlayersReady()) {
                    namespace.broadcast(null, "map", map.toJson());
                    lobby.startGame();

                    selectQuestionListener(socket);
                }
            } catch (JAXBException e) {
                e.printStackTrace();
            }
        });
    }

    private void selectQuestionListener(SocketIoSocket socket) {
        socket.on("select", args -> {
            if (lobby.getActivePlayer().equals(getPlayer(socket))) {
                long answerDelay = 20000;
                JSONObject request = (JSONObject) args[0];
                String theme = request.getString("theme");
                int questionIndex = request.getInt("question");
                Atom questionAtom = lobby.getQuestion(theme, questionIndex).getAtoms().get(0);
                namespace.broadcast(null, "question", new AtomDto(questionAtom, answerDelay).toJson());
                TimerTask timerTask = new TimerTask() {
                    @Override
                    public void run() {
                        namespace.broadcast(null, "answer", new LobbyDto(lobby).toJson());
                    }
                };
                timer.schedule(timerTask, answerDelay);
            }
        });
    }

//    private void answerListener(SocketIoSocket socket) {
//        socket.on("answer", args -> {
//            lobby.getActivePlayer().setAnswer(answer);
//            namespace.broadcast(null, "answer", new JSONObject().put("answer", answer));
//        });
//    }

    private Player getPlayer(SocketIoSocket socket) {
        return playerService.getPlayer(socket.getInitialHeaders().get("username").get(0));
    }

//    public void endGame() {
//        socketServer.deleteLobbyNamespace(lobby);
//    }
}
