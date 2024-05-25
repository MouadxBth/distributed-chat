package me.mouad;

import me.mouad.api.Client;
import me.mouad.api.Server;

import javax.swing.*;
import java.io.FileOutputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class ChatClient extends UnicastRemoteObject implements Client {

    private final ChatView view;

    private static final Logger logger = Logger.getLogger(ChatClient.class.getName());

    static {
        try {
            final FileHandler fileHandler = new FileHandler("client_logs.log");

            fileHandler.setFormatter(new SimpleFormatter());

            logger.addHandler(fileHandler);
            logger.setLevel(Level.ALL);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to initialize logger", e);
        }
    }

    protected ChatClient(ChatView view) throws RemoteException {
        this.view = view;
    }

    @Override
    public void receiveMessage(String sender, String message) throws RemoteException {
        view.appendMessage(sender, message);
    }

    @Override
    public void receiveFile(String sender, byte[] fileData, String fileName) throws RemoteException {
        try {
            final String filePath = System.getProperty("java.io.tmpdir") + fileName;
            final FileOutputStream fos = new FileOutputStream(filePath);

            fos.write(fileData);
            fos.close();

            view.appendFileLink(sender, fileName, filePath);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to receive file: " + fileName, e);
        }
    }

    @Override
    public void updateConnectedUsersList(List<String> users) throws RemoteException {
        this.view.updateConnectedUsersList(users);
    }


    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            final String username = JOptionPane.showInputDialog("Enter your username:");

            if (username == null || username.trim().isBlank() || username.equals("Server")) {
                logger.log(Level.SEVERE, "Invalid username");
                System.exit(0);
            }

            try {
                final Server server = (Server) LocateRegistry.getRegistry().lookup("ChatServer");
                final ChatView chatView = new ChatView(server, username);
                final Client client = new ChatClient(chatView);

                server.register(username, client);
                chatView.setVisible(true);

            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to configure RMI", e);
                JOptionPane.showMessageDialog(new JFrame(), "Error: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
            }
        });
    }
}
