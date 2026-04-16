package network;

import utils.JsonMensagem;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Thread que escuta o grupo multicast UDP e exibe os avisos recebidos.
 *
 * Tipos de mensagem tratados:
 *   "AVISO" — aviso de texto simples do admin (caixa de uma linha)
 *   "TIMES" — resultado dos times gerados (caixa multi-linha)
 *
 * Roda em background durante toda a sessao do cliente.
 * Chame parar() antes de encerrar o programa para liberar o socket.
 */
public class ClienteMulticast implements Runnable {

    private volatile boolean ativo = true;

    public void parar() {
        ativo = false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void run() {
        try (MulticastSocket socket = new MulticastSocket(Protocolo.MULTICAST_PORTA)) {
            InetAddress grupo = InetAddress.getByName(Protocolo.MULTICAST_GROUP);
            socket.joinGroup(grupo);
            socket.setSoTimeout(1000); // Checa o flag ativo a cada 1 segundo

            byte[] buf = new byte[8192]; // Buffer maior para mensagens de times

            while (ativo) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(packet);
                    String json = new String(packet.getData(), 0, packet.getLength(),
                            StandardCharsets.UTF_8);
                    JsonMensagem msg = JsonMensagem.fromJson(json);

                    if ("TIMES".equals(msg.tipo)) {
                        exibirTimes(msg);
                    } else {
                        exibirAviso(msg);
                    }
                } catch (SocketTimeoutException ignored) {
                    // Timeout intencional para checar ativo
                }
            }

            socket.leaveGroup(grupo);

        } catch (IOException e) {
            if (ativo) System.err.println("[MULTICAST] Erro ao receber: " + e.getMessage());
        }
    }

    private static void exibirAviso(JsonMensagem msg) {
        System.out.printf("%n╔══════════════════════════════════════════╗%n");
        System.out.printf( "║  AVISO de %-10s  [%s]     ║%n", msg.de, msg.hora);
        System.out.printf( "║  %-40s  ║%n", msg.mensagem);
        System.out.printf( "╚══════════════════════════════════════════╝%n> ");
    }

    private static void exibirTimes(JsonMensagem msg) {
        System.out.printf("%n╔══════════════════════════════════════════╗%n");
        System.out.printf( "║        TIMES GERADOS  [%s]         ║%n", msg.hora);
        System.out.printf( "╠══════════════════════════════════════════╣%n");
        // Cada linha da mensagem já está formatada pelo servidor
        for (String linha : msg.mensagem.split("\n")) {
            System.out.printf("║  %-40s║%n", linha);
        }
        System.out.printf( "╚══════════════════════════════════════════╝%n> ");
    }
}
