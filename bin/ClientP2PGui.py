import tkinter as tk
from tkinter import messagebox, scrolledtext, font
import socket
import threading
import time

class ClientP2PGui:
    """
    ClientP2PGui - Implementação de um cliente de chat P2P com interface gráfica em Python. Esta classe fornece uma interface de usuário Tkinter para comunicação em rede ponto a ponto.
    """
    def __init__(self, local_host, local_port, peers):
        """
        Inicializa o cliente P2P.
        
        Args:
            local_host (str): Endereço IP local
            local_port (int): Porta local para escutar conexões
            peers (list): Lista de tuplas (host, port) dos peers para conectar
        """
        self.local_host = local_host    
        self.local_port = local_port
        self.peers = peers
        
        # Configurações de rede
        self.server_socket = None           # Socket servidor
        self.peers_connections = {}         # Dicionário de conexões ativas
        self.running = True                 # Flag de controle do loop principal

        # Configuração da janela principal
        self.root = tk.Tk()
        self.root.title("P2P Chat - Não Conectado")
        self.root.geometry("500x600")
        self.root.configure(bg='#f0f0f0')

        # Definir fonte personalizada
        self.default_font = font.Font(family="Arial", size=10)
        self.root.option_add("*Font", self.default_font)

        self.create_widgets()
        self.setup_networking()

    def create_widgets(self):
        """Cria e configura todos os widgets da interface gráfica."""
        # Painel de configuração (estilo mais próximo do Java)
        config_frame = tk.Frame(self.root, bg='#f0f0f0')
        config_frame.pack(fill=tk.X, padx=10, pady=10)

        username_label = tk.Label(config_frame, text="Nome de Usuário:", bg='#f0f0f0')
        username_label.pack(side=tk.LEFT, padx=(0, 5))

        self.username_entry = tk.Entry(config_frame, width=15)
        self.username_entry.pack(side=tk.LEFT, padx=5)

        self.connect_button = tk.Button(
            config_frame, 
            text="Conectar", 
            command=self.configure_username,
            bg='#e1e1e1',
            activebackground='#d0d0d0'
        )
        self.connect_button.pack(side=tk.LEFT)

        # Área de chat com mais espaçamento e estilo
        self.chat_area = scrolledtext.ScrolledText(
            self.root, 
            state='disabled', 
            wrap=tk.WORD,
            background='white',
            font=("Consolas", 10)
        )
        self.chat_area.pack(expand=True, fill=tk.BOTH, padx=10, pady=10)

        # Painel de mensagem com layout mais próximo do Java
        message_frame = tk.Frame(self.root, bg='#f0f0f0')
        message_frame.pack(fill=tk.X, padx=10, pady=10)

        self.message_entry = tk.Entry(
            message_frame, 
            width=40,
            font=self.default_font
        )
        self.message_entry.pack(side=tk.LEFT, expand=True, fill=tk.X, padx=(0, 5))
        self.message_entry.bind('<Return>', self.send_message)
        self.message_entry.config(state='disabled')

        self.send_button = tk.Button(
            message_frame, 
            text="Enviar", 
            command=self.send_message,
            bg='#e1e1e1',
            activebackground='#d0d0d0'
        )
        self.send_button.pack(side=tk.RIGHT)
        self.send_button.config(state='disabled')

    def configure_username(self):
        """Configura o nome de usuário e habilita a interface de chat."""
        username = self.username_entry.get().strip()
        if username:
            self.root.title(f"P2P Chat - {username}")
            self.username_entry.config(state='disabled')
            self.connect_button.config(state='disabled')
            self.message_entry.config(state='normal')
            self.send_button.config(state='normal')
            self.message_entry.focus()  # Coloca o foco no campo de mensagem
        else:
            messagebox.showerror("Erro", "Por favor, insira um nome de usuário")

    def setup_networking(self):
        """Configura a infraestrutura de rede, iniciando o servidor e as conexões."""
        self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.server_socket.bind((self.local_host, self.local_port))
        self.server_socket.listen(5)
        
        self.log(f"[SERVIDOR] Escutando em {self.local_host}:{self.local_port}")

        # Threads para conexões e recebimento de mensagens
        threading.Thread(target=self.accept_connections, daemon=True).start()
        threading.Thread(target=self.connect_to_peers, daemon=True).start()

    def accept_connections(self):
        """Loop principal para aceitar conexões de entrada."""
        while self.running:
            try:
                conn, addr = self.server_socket.accept()
                self.log(f"[NOVA CONEXÃO] Recebida de {addr}")
                threading.Thread(target=self.handle_peer_connection, args=(conn, addr), daemon=True).start()
            except Exception as e:
                if self.running:
                    self.log(f"[ERRO] Ao aceitar conexão: {e}")
                time.sleep(1)

    def handle_peer_connection(self, conn, addr):
        """
        Gerencia uma conexão específica com um peer.
        
        Args:
            conn (socket): Socket da conexão
            addr (tuple): Endereço do peer (host, port)
        """
        try:
            while self.running:
                data = conn.recv(1024).decode('utf-8').strip()
                if not data:
                    break
                self.log(data)
        except Exception as e:
            self.log(f"[ERRO] Conexão com {addr} perdida: {e}")
        finally:
            conn.close()

    def connect_to_peers(self):
        """Inicia conexões com todos os peers conhecidos."""
        for peer in self.peers:
            host, port = peer
            try:
                sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                sock.connect((host, port))
                self.peers_connections[(host, port)] = sock
                self.log(f"[CONEXÃO] Conectado a {host}:{port}")

                threading.Thread(target=self.receive_messages, args=(sock, peer), daemon=True).start()
            except Exception as e:
                self.log(f"[ERRO] Não foi possível conectar a {host}:{port}: {e}")

    def receive_messages(self, sock, peer):
        """
        Gerencia o recebimento de mensagens de um peer específico.
        
        Args:
            sock (socket): Socket da conexão
            peer (tuple): Endereço do peer (host, port)
        """
        try:
            while self.running:
                data = sock.recv(1024).decode('utf-8').strip()
                if not data:
                    break
                self.log(data)
        except Exception as e:
            self.log(f"[ERRO] Falha ao receber mensagens de {peer}: {e}")
        finally:
            sock.close()
            del self.peers_connections[peer]

    def send_message(self, event=None):
        """
        Envia uma mensagem para todos os peers conectados.
        
        Args:
            event: Evento de tecla (opcional, usado para bind do Enter)
        """
        message = self.message_entry.get().strip()
        if message:
            username = self.root.title().replace("P2P Chat - ", "")
            full_message = f"{username}: {message}"
            
            for (host, port), sock in list(self.peers_connections.items()):
                try:
                    sock.sendall(full_message.encode('utf-8') + b'\n')
                except Exception as e:
                    self.log(f"[ERRO] Falha ao enviar mensagem para {host}:{port}: {e}")
            
            self.log(f"Você: {message}")
            self.message_entry.delete(0, tk.END)

    def log(self, message):
        """
        Registra uma mensagem na área de chat.
        
        Args:
            message (str): Mensagem a ser exibida
        """
        def update_chat():
            self.chat_area.config(state='normal')
            self.chat_area.insert(tk.END, message + "\n")
            self.chat_area.config(state='disabled')
            self.chat_area.see(tk.END)
        
        self.root.after(0, update_chat)

    def run(self):
        """Inicia o loop principal da aplicação."""
        self.root.mainloop()
        self.running = False
        
        # Fechar todas as conexões
        for sock in self.peers_connections.values():
            sock.close()

def main():
    """Função main para iniciar a aplicação."""
    client = ClientP2PGui('127.0.0.1', 6787, [('127.0.0.1', 6789)])
    client.run()

if __name__ == "__main__":
    main()