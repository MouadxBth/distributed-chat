package me.mouad;

import me.mouad.api.Server;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.*;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.rmi.RemoteException;

import java.util.Arrays;
import java.util.Collection;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class ChatView extends JFrame {

    static class InvisibleCaret extends DefaultCaret {
        @Override
        public void paint(Graphics g) {}
    }

    private final JPanel mainPanel;
    private JList<String> connectedUsersList;
    private JTextPane messagesTextPane;
    private JTextField messageTextField;
    private JButton sendButton;
    private JButton attachButton;

    private final GridBagConstraints gridBagConstraints;

    private final String username;

    private final Server server;

    private static final Logger logger = Logger.getLogger(ChatView.class.getName());

    static {
        try {
            final FileHandler fileHandler = new FileHandler("chat_view_logs.log");

            fileHandler.setFormatter(new SimpleFormatter());

            logger.addHandler(fileHandler);
            logger.setLevel(Level.ALL);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to initialize logger", e);
        }
    }

    public ChatView(Server server, String username) {
        this.server = server;
        this.username = username;
        this.mainPanel = new JPanel(new GridBagLayout());
        this.gridBagConstraints = new GridBagConstraints();

        configureFrame();
        configureGridBagConstraints();
        configureMessagesPanel();
        configureConnectedUsersPanel();
        configureMessageInputPanel();
        configureInputActions();
        configureLookAndFeel();

        add(mainPanel);
    }

    private void configureFrame() {
        setTitle("Chat (" + username + ")");
        setResizable(false);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        //setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        setSize(800, 600);
    }

    private void configureGridBagConstraints() {
        this.gridBagConstraints.fill = GridBagConstraints.BOTH;
        this.gridBagConstraints.weightx = 1.0;
        this.gridBagConstraints.weighty = 1.0;
        this.gridBagConstraints.insets = new Insets(5, 5, 5, 5);
    }

    private void configureMessagesPanel() {
        final JPanel messagesPanel = new JPanel(new BorderLayout());
        final JLabel messagesLabel = new JLabel("Messages:");

        messagesLabel.setHorizontalAlignment(SwingConstants.CENTER);
        messagesPanel.add(messagesLabel, BorderLayout.NORTH);

        messagesTextPane = new JTextPane();
        messagesTextPane.setEditable(false);
        messagesTextPane.setContentType("text/html");
        messagesTextPane.setEditorKit(new HTMLEditorKit());

        messagesTextPane.setCaret(new InvisibleCaret());

        final JScrollPane messagesScrollPane = new JScrollPane(messagesTextPane);
        messagesPanel.add(messagesScrollPane, BorderLayout.CENTER);

        addToMainPanel(messagesPanel, 1, 0, 1);
    }

    private void configureConnectedUsersPanel() {
        final JPanel connectedUsersPanel = new JPanel(new BorderLayout());
        final JLabel connectedUsersLabel = new JLabel("Connected Users");

        connectedUsersLabel.setHorizontalAlignment(SwingConstants.CENTER);
        connectedUsersPanel.add(connectedUsersLabel, BorderLayout.NORTH);

        connectedUsersList = new JList<>();
        JScrollPane connectedUsersScrollPane = new JScrollPane(connectedUsersList);
        connectedUsersPanel.add(connectedUsersScrollPane, BorderLayout.CENTER);

        this.gridBagConstraints.weightx = 0.0;
        addToMainPanel(connectedUsersPanel, 0, 0, 1);
        this.gridBagConstraints.weightx = 1.0;
    }

    private void configureMessageInputPanel() {
        final JPanel messageInputPanel = new JPanel(new BorderLayout());

        messageTextField = new JTextField();
        messageTextField.setColumns(15);

        attachButton = new JButton("Attach File");
        sendButton = new JButton("Send");

        final JPanel inputFieldPanel = new JPanel(new BorderLayout());

        inputFieldPanel.add(messageTextField, BorderLayout.CENTER);
        inputFieldPanel.add(attachButton, BorderLayout.EAST);

        messageInputPanel.add(inputFieldPanel, BorderLayout.CENTER);
        messageInputPanel.add(sendButton, BorderLayout.EAST);

        this.gridBagConstraints.weighty = 0.0;
        addToMainPanel(messageInputPanel, 0, 1, 2);
        this.gridBagConstraints.weighty = 1.0;
    }

    private void addToMainPanel(JComponent component, int gridx, int gridy, int gridwidth) {
        gridBagConstraints.gridwidth = gridwidth;
        gridBagConstraints.gridx = gridx;
        gridBagConstraints.gridy = gridy;
        mainPanel.add(component, gridBagConstraints);
    }

    private void configureInputActions() {
        final ActionListener textSubmissionListener = _ -> {
            String message = messageTextField.getText();
            if (!message.isEmpty()) {
                try {
                    server.broadcastMessage(username, message);
                    messageTextField.setText("");
                } catch (RemoteException ex) {
                    logger.log(Level.SEVERE, "Unable to broadcast message: ", ex);
                }
            }
        };

        sendButton.addActionListener(textSubmissionListener);
        messageTextField.addActionListener(textSubmissionListener); // Enter keypress

        attachButton.addActionListener(_ -> {
            final JFileChooser fileChooser = new JFileChooser();
            final int decision = fileChooser.showOpenDialog(ChatView.this);

            if (decision == JFileChooser.APPROVE_OPTION) {
                final File file = fileChooser.getSelectedFile();
                handleFileUpload(file);
            }
        });

        messagesTextPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, "Unable to open file: " + e.getURL(), ex);
                }
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    server.unregister(username);
                } catch (RemoteException ex) {
                    logger.log(Level.SEVERE, "Unable to unregister client", ex);
                }
            }
        });
    }

    private void handleFileUpload(File file) {
        try {
            server.broadcastFile(username, Files.readAllBytes(file.toPath()), file.getName());
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to handle an uploaded file", e);
        }
    }

    public void appendMessage(String sender, String message) {
        try {
            final HTMLDocument document = (HTMLDocument) messagesTextPane.getDocument();
            final HTMLEditorKit editorKit = (HTMLEditorKit) messagesTextPane.getEditorKit();
            final String color = getColor(sender);

            final String styledMessage = "<span style='font-weight:bold; color:" + color + ";'>" + sender + ":</span> " + message;

            editorKit.insertHTML(document,
                    document.getLength(),
                    styledMessage,
                    0,
                    0,
                    null);

            messagesTextPane.setCaretPosition(document.getLength()); // Auto scroll
        } catch (BadLocationException | IOException e) {
            logger.log(Level.SEVERE, "Unable to display a new message", e);
        }
    }

    public void appendFileLink(String sender, String fileName, String filePath) {
        try {
            final File tempFile = new File(System.getProperty("java.io.tmpdir"), fileName);
            Files.copy(Path.of(filePath), tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            final String fileLink = "<a href='file:///" + tempFile.getAbsolutePath().replace("\\", "/") + "' download='" + fileName + "'>" + fileName + "</a>";

            final String mimeType = Files.probeContentType(tempFile.toPath());

            if (mimeType != null && mimeType.startsWith("image")) {
                final String imageTag = "<img src='file:///" + tempFile.getAbsolutePath().replace("\\", "/") + "' width='200'/>";
                appendMessage(sender ,"attached an image: " + imageTag + "<br>Download: " + fileLink);
                return ;
            }

            appendMessage(sender ,"attached: " + fileLink);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to display an uploaded file", e);
        }
    }

    public void updateConnectedUsersList(Collection<String> users) {
        connectedUsersList.setListData(users.toArray(new String[0]));
    }

    private String getColor(String sender) {
        if (sender.equals("Server")) return "#FF0000";

        return sender.equals(username) ? "#0000FF" : "#36454F";
    }

    private void configureLookAndFeel() {
        Arrays.stream(UIManager.getInstalledLookAndFeels())
                .filter(info -> info.getName().equals("Windows"))
                .findAny()
                .ifPresent(laf -> {
                    try {
                        UIManager.setLookAndFeel(laf.getClassName());
                    } catch (ClassNotFoundException | UnsupportedLookAndFeelException |
                             IllegalAccessException | InstantiationException e) {
                        logger.log(Level.SEVERE, "Unable to configure look and feel", e);
                    }
                });
    }
}



