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
    private static final int LOCAL_PORT = 6789;             // Porta local para escutar conexões             
    private static final String LOCAL_HOST = "127.0.0.1";   // Endereço IP local
    private final List<String> peerAddresses;               // Lista de endereços dos peers para conectar
    private ServerSocket serverSocket;                      // Socket servidor para aceitar conexões
    private final ExecutorService executorService;          // Pool de threads para gerenciar conexões
    private final List<PeerConnection> peerConnections;     // Lista de conexões ativas com peers
    private volatile boolean running = true;                // Flag para controle do loop principal

    // Componentes Swing
    private JTextField usernameField;                   // Campo para nome de usuário
    private JTextField peerHostField;                   // Campo para endereço IP do peer
    private JTextField peerPortField;                   // Campo para porta de conexão do peer
    private JTextField messageField;                    // Campo para digitar mensagens
    private JTextArea chatArea;                         // Área de exibição do chat
    private JButton sendButton;                         // Botão de enviar mensagem
    private JButton connectButton;                      // Botão de conectar
    private JButton addPeerButton;                      // Botão de adicioinar peer
    private DefaultListModel<String> peerListModel;
    private JList<String> peerList;

    public ClientP2PGui() {
        this.executorService = Executors.newCachedThreadPool();
        this.peerConnections = new ArrayList<>();
        this.peerAddresses = new ArrayList<>();
        initComponents();
        setupNetworking();
    }

    private void initComponents() {
        setTitle("P2P Chat - Não Conectado");
        setSize(600, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Painel de Configuração
        JPanel configPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Linha 1: Username
        gbc.gridx = 0; gbc.gridy = 0;
        configPanel.add(new JLabel("Nome de Usuário:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2;
        usernameField = new JTextField(15);
        configPanel.add(usernameField, gbc);

        // Linha 2: Peer Host/Port
        gbc.gridy = 1; gbc.gridx = 0; gbc.gridwidth = 1;
        configPanel.add(new JLabel("IP do Par:"), gbc);
        gbc.gridx = 1;
        peerHostField = new JTextField(10);
        configPanel.add(peerHostField, gbc);
        gbc.gridx = 2;
        configPanel.add(new JLabel("Porta do Par:"), gbc);
        gbc.gridx = 3;
        peerPortField = new JTextField(5);
        configPanel.add(peerPortField, gbc);

        // Botão Adicionar Par
        gbc.gridy = 1; gbc.gridx = 4;
        addPeerButton = new JButton("Adicionar");
        configPanel.add(addPeerButton, gbc);

        // Lista de Peers
        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 5;
        peerListModel = new DefaultListModel<>();
        peerList = new JList<>(peerListModel);
        peerList.setVisibleRowCount(3);
        JScrollPane peerScrollPane = new JScrollPane(peerList);
        configPanel.add(peerScrollPane, gbc);

        // Botão Conectar
        gbc.gridy = 3; gbc.gridx = 1; gbc.gridwidth = 2;
        connectButton = new JButton("Conectar");
        configPanel.add(connectButton, gbc);

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
        addPeerButton.addActionListener(e -> addPeer());
        connectButton.addActionListener(e -> startConnection());
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());

        // Estado inicial
        messageField.setEnabled(false);
        sendButton.setEnabled(false);
        log("[INFO] Servidor local iniciado em " + LOCAL_HOST + ":" + LOCAL_PORT);
    }

    private void addPeer() {
        try {
            String host = peerHostField.getText().trim();
            int port = Integer.parseInt(peerPortField.getText().trim());
            
            if (host.isEmpty()) {
                JOptionPane.showMessageDialog(this, 
                    "Por favor, insira o IP do par", 
                    "Erro", 
                    JOptionPane.ERROR_MESSAGE);
                return;
            }

            String peerAddress = host + ":" + port;
            if (!peerListModel.contains(peerAddress)) {
                peerListModel.addElement(peerAddress);
                peerAddresses.add(peerAddress);
                peerHostField.setText("");
                peerPortField.setText("");
                log("[INFO] Par adicionado: " + peerAddress);
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this,
                "Por favor, insira uma porta válida",
                "Erro",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void startConnection() {
        String username = usernameField.getText().trim();
        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Por favor, insira um nome de usuário", 
                "Erro", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (peerListModel.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "Por favor, adicione pelo menos um par", 
                "Erro", 
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Iniciar conexões com os peers
        connectToPeers();
            
        // Atualizar interface
        setTitle("P2P Chat - " + username);
        usernameField.setEnabled(false);
        peerHostField.setEnabled(false);
        peerPortField.setEnabled(false);
        addPeerButton.setEnabled(false);
        connectButton.setEnabled(false);
        messageField.setEnabled(true);
        sendButton.setEnabled(true);
        messageField.requestFocus();
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
            serverSocket = new ServerSocket(LOCAL_PORT);
            serverSocket.setReuseAddress(true);
            log("[SERVIDOR] Escutando na porta " + LOCAL_PORT);

            // Thread para aceitar conexões
            executorService.submit(this::acceptConnections);
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
            ClientP2PGui client = new ClientP2PGui();
            client.setVisible(true);
        });
    }
}