package network;

import models.Jogador;
import streams.JogadorInputStream;
import streams.JogadorOutputStream;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Servidor TCP da Fase 4.
 *
 * Protocolo:
 *   1. Aguarda conexão do cliente
 *   2. Desempacota a requisição (array de Jogadores) via JogadorInputStream
 *   3. Empacota e envia a resposta (eco dos mesmos Jogadores) via JogadorOutputStream
 */
public class Servidor {

    static final int PORTA = 5000;

    public static void main(String[] args) throws IOException {
        System.out.println("Servidor aguardando conexao na porta " + PORTA + "...");

        try (ServerSocket serverSocket = new ServerSocket(PORTA);
             Socket socket = serverSocket.accept()) {

            System.out.println("Cliente conectado: " + socket.getInetAddress());

            // Desempacota requisicao
            JogadorInputStream jis = new JogadorInputStream(socket.getInputStream());
            Jogador[] recebidos = jis.receber();

            System.out.println("Recebidos " + recebidos.length + " jogadores:");
            for (Jogador j : recebidos) {
                System.out.println("  " + j);
            }

            // Empacota e envia resposta (eco)
            JogadorOutputStream jos = new JogadorOutputStream(recebidos, recebidos.length, socket.getOutputStream());
            jos.enviar();

            System.out.println("Resposta enviada. Conexao encerrada.");
        }
    }
}
