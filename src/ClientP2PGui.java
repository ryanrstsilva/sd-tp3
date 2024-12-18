import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.swing.*;

/**
 * ClientP2PGui - Implementação de um cliente de chat P2P com interface gráfica em Java.
 * Esta classe fornece uma interface de usuário Swing para comunicação em rede ponto a ponto.
 */
public class ClientP2PGui extends JFrame {
    // Configurações de rede
    private final int localPort;                        
    private final String localHost;
    private final List<String> peerAddresses;
    private ServerSocket serverSocket;
    private final ExecutorService executorService;
    private final List<PeerConnection> peerConnections;
    private volatile boolean running = true;

    // Componentes Swing
    private JTextField usernameField;
    private JTextField messageField;
    private JTextArea chatArea;
    private JButton sendButton;
    private JButton connectButton;

    public ClientP2PGui(String localHost, int localPort, List<String> peerAddresses) {
        this.localHost = localHost;
        this.localPort = localPort;
        this.peerAddresses = peerAddresses;
        this.executorService = Executors.newCachedThreadPool();
        this.peerConnections = new ArrayList<>();

        initComponents();
        setupNetworking();
    }

    private void initComponents() {
        setTitle("P2P Chat - Não Conectado");
        setSize(500, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Painel de Configuração
        JPanel configPanel = new JPanel(new FlowLayout());
        usernameField = new JTextField(15);
        connectButton = new JButton("Conectar");
        configPanel.add(new JLabel("Nome de Usuário:"));
        configPanel.add(usernameField);
        configPanel.add(connectButton);

        // Área de Chat
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);

        // Painel de Mensagem
        JPanel messagePanel = new JPanel(new BorderLayout());
        messageField = new JTextField();
        sendButton = new JButton("Enviar");
        messagePanel.add(messageField, BorderLayout.CENTER);
        messagePanel.add(sendButton, BorderLayout.EAST);

        // Adicionar componentes
        add(configPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(messagePanel, BorderLayout.SOUTH);

        // Listeners
        connectButton.addActionListener(e -> configureUsername());
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());

        // Estado inicial
        messageField.setEnabled(false);
        sendButton.setEnabled(false);
    }

    private void configureUsername() {
        String username = usernameField.getText().trim();
        if (!username.isEmpty()) {
            setTitle("P2P Chat - " + username);
            usernameField.setEnabled(false);
            connectButton.setEnabled(false);
            messageField.setEnabled(true);
            sendButton.setEnabled(true);
        } else {
            JOptionPane.showMessageDialog(this, 
                "Por favor, insira um nome de usuário", 
                "Erro", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setupNetworking() {
        try {
            serverSocket = new ServerSocket(localPort);
            serverSocket.setReuseAddress(true);
            log("[SERVIDOR] Escutando na porta " + localPort);

            // Thread para aceitar conexões
            executorService.submit(this::acceptConnections);
            connectToPeers();
        } catch (IOException e) {
            log("[ERRO] Falha ao iniciar servidor: " + e.getMessage());
        }
    }

    private void acceptConnections() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                log("[NOVA CONEXÃO] " + clientSocket.getInetAddress());
                executorService.submit(() -> handleIncomingConnection(clientSocket));
            } catch (IOException e) {
                if (running) {
                    log("[ERRO] Falha ao aceitar conexão: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Gerencia uma conexão de entrada específica.
     * @param socket Socket da conexão estabelecida
     */
    private void handleIncomingConnection(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                final String message = inputLine;
                SwingUtilities.invokeLater(() -> log(message));
            }
        } catch (IOException e) {
            log("[ERRO] Conexão perdida: " + e.getMessage());
        }
    }

    /**
     * Inicia conexões com todos os peers conhecidos.
     */
    private void connectToPeers() {
        for (String peerAddress : peerAddresses) {
            String[] parts = peerAddress.split(":");
            PeerConnection peerConn = new PeerConnection(parts[0], Integer.parseInt(parts[1]));
            executorService.submit(() -> {
                for (int attempt = 0; attempt < 5; attempt++) {
                    if (peerConn.connect()) {
                        synchronized (peerConnections) {
                            peerConnections.add(peerConn);
                        }
                        break;
                    }
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }
    }

    /**
     * Envia uma mensagem para todos os peers conectados.
     */
    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            String username = getUsername();
            String fullMessage = username + ": " + message;
            
            synchronized (peerConnections) {
                for (PeerConnection peer : peerConnections) {
                    peer.sendMessage(fullMessage);
                }
            }
            
            log("Você: " + message);
            messageField.setText("");
        }
    }

    /**
     * Obtém o nome de usuário atual.
     * @return Nome do usuário configurado
     */
    private String getUsername() {
        return getTitle().replace("P2P Chat - ", "");
    }

    /**
     * Registra uma mensagem na área de chat.
     * @param message Mensagem a ser exibida
     */
    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    /**
     * Classe interna que gerencia uma conexão individual com um peer.
     */
    private class PeerConnection {
        private final String host;  // Endereço do peer
        private final int port;     // Porta do peer
        private Socket socket;      // Socket da conexão
        private PrintWriter out;    // Stream de saída
        private volatile boolean connected = false; // Estado da conexão

        public PeerConnection(String host, int port) {
            this.host = host;
            this.port = port;
        }

        public boolean connect() {
            try {
                socket = new Socket(host, port);
                out = new PrintWriter(socket.getOutputStream(), true);
                connected = true;
                log("[CONEXÃO] Conectado a " + host + ":" + port);

                new Thread(() -> {
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                        String inputLine;
                        while ((inputLine = in.readLine()) != null && connected) {
                            final String message = inputLine;
                            SwingUtilities.invokeLater(() -> log(message));
                        }
                    } catch (IOException e) {
                        log("[ERRO] Falha ao receber mensagens de " + host + ":" + port);
                    } finally {
                        disconnect();
                    }
                }).start();

                return true;
            } catch (IOException e) {
                log("[ERRO] Não foi possível conectar a " + host + ":" + port);
                return false;
            }
        }

        public void sendMessage(String message) {
            if (connected && out != null) {
                out.println(message);
            }
        }

        public void disconnect() {
            connected = false;
            try {
                if (socket != null) socket.close();
            } catch (IOException e) {
                log("[ERRO] Falha ao desconectar");
            }
        }
    }

    /**
     * Método main para iniciar a aplicação.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ClientP2PGui client = new ClientP2PGui("127.0.0.1", 6789, Arrays.asList("127.0.0.1:6787"));
            client.setVisible(true);
        });
    }
}