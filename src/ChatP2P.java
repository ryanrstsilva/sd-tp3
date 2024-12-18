import java.io.PrintWriter;
import java.net.Socket;

import javax.swing.JFrame;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class ChatP2P {
    private JFrame frame = new JFrame("Java P2P Chat Client");
    private JTextField textField = new JTextField(50);
    private JTextArea messageArea = new JTextArea(16, 50);
    private Socket socket;
    private BuffereadReader in;
    private PrintWriter out;

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Uso: java ChatP2P");
        }
    }
}
