package me.mouad;

import me.mouad.api.Client;
import me.mouad.api.Server;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import java.util.ArrayList;
import java.util.HashMap;

import java.util.List;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class ChatServer extends UnicastRemoteObject implements Server {

    private final Map<String, Client> clients = new HashMap<>();
    private final List<String> chatHistory = new ArrayList<>();
    private final String HISTORY_FILE = "chat_history.txt";

    private static final Logger logger = Logger.getLogger(ChatServer.class.getName());

    static {
        try {
            final FileHandler fileHandler = new FileHandler("server_logs.log");

            fileHandler.setFormatter(new SimpleFormatter());

            logger.addHandler(fileHandler);
            logger.setLevel(Level.ALL);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to initialize logger", e);
        }
    }

    protected ChatServer() throws RemoteException {
        loadChatHistory();
    }

    private void loadChatHistory() {
        try {
            final File file = new File(HISTORY_FILE);

            if (!file.exists()) {
                logger.warning("No chat history found.");
                return ;
            }

            chatHistory.addAll(Files.readAllLines(file.toPath()));

            logger.info("Chat history loaded.");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error loading chat history", e);
        }
    }

    private void saveChatHistory() {
        try (final BufferedWriter writer = new BufferedWriter(new FileWriter(HISTORY_FILE))) {
            for (String message : chatHistory) {
                writer.write(message);
                writer.newLine();
            }

            logger.info("Chat history saved.");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Error saving chat history", e);
        }
    }


    @Override
    public synchronized void register(String username, Client client) throws RemoteException {
        if (clients.containsKey(username)) {
            throw new RemoteException("Username '" + username + "' is already taken.");
        }

        clients.put(username, client);
        updateConnectedUsersList();

        sendChatHistory(client);

        broadcastMessage("Server", username + " has joined the server!");
        logger.info("Client registered: " + username);
    }

    @Override
    public void unregister(String username) throws RemoteException {
        clients.remove(username);
        updateConnectedUsersList();

        broadcastMessage("Server", username + " has left the server!");
        logger.info("Client unregistered: " + username);
    }

    @Override
    public void broadcastMessage(String sender, String message) throws RemoteException {
        logger.info("Broadcasting message: " + message);

        chatHistory.add(sender + ": " + message);

        for (Client client : clients.values()) {
            client.receiveMessage(sender, message);
        }
    }

    @Override
    public void broadcastFile(String sender, byte[] fileData, String fileName) throws RemoteException {
        logger.info("Broadcasting file: " + fileName);

        chatHistory.add("#Attached " + fileName + " " + sender);

        for (Client client : clients.values()) {
            client.receiveFile(sender, fileData, fileName);
        }
    }

    private void sendChatHistory(Client client) throws RemoteException {
        for (String message : chatHistory) {
            if (!message.startsWith("#Attached")) {
                final String sender = message.split(": ")[0];

                client.receiveMessage(sender, message.substring(sender.length()));

                continue;
            }

            final String[] parts = message.substring("#Attached ".length()).split(" ");

            final String fileName = parts[0];
            final String sender = parts[1];

            try {
                final Path filePath = Paths.get(System.getProperty("java.io.tmpdir"), fileName);
                final byte[] fileData = Files.readAllBytes(filePath);
                client.receiveFile(sender, fileData, fileName);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error sending file to client", e);
            }
        }
    }

    private void updateConnectedUsersList() throws RemoteException {
        for (Client client : clients.values()) {
            client.updateConnectedUsersList(clients.keySet().stream().toList());
        }
    }

    public static void main(String[] args) {
        try {
            final ChatServer server = new ChatServer();
            LocateRegistry.createRegistry(1099);

            Runtime.getRuntime().addShutdownHook(new Thread(server::saveChatHistory));

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Server is shutting down...");

                try {
                    UnicastRemoteObject.unexportObject(server, true);
                    logger.info("Server is stopped.");
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error stopping server", e);
                }
            }));

            final Registry registry = LocateRegistry.getRegistry();
            registry.rebind("ChatServer", server);

            logger.info("ChatServer ready. Listening on port 1099.");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error starting server", e);
        }
    }
}
