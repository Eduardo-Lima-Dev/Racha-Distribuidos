package network;

import models.Jogador;
import streams.JogadorInputStream;
import streams.JogadorOutputStream;

import java.io.IOException;
import java.net.Socket;

/**
 * Cliente TCP da Fase 4.
 *
 * Protocolo:
 *   1. Conecta ao servidor
 *   2. Empacota e envia a requisicao (array de Jogadores) via JogadorOutputStream
 *   3. Desempacota a resposta do servidor via JogadorInputStream
 */
public class Cliente {

    static final String HOST = "localhost";

    public static void main(String[] args) throws IOException {
        Jogador[] jogadores = criarJogadores();

        System.out.println("Conectando ao servidor " + HOST + ":" + Servidor.PORTA + "...");

        try (Socket socket = new Socket(HOST, Servidor.PORTA)) {
            System.out.println("Conectado!");

            JogadorOutputStream jos = new JogadorOutputStream(jogadores, jogadores.length, socket.getOutputStream());
            jos.enviar();
            System.out.println("Enviados " + jogadores.length + " jogadores ao servidor.");

            JogadorInputStream jis = new JogadorInputStream(socket.getInputStream());
            Jogador[] resposta = jis.receber();

            System.out.println("Resposta recebida (" + resposta.length + " jogadores):");
            for (Jogador j : resposta) {
                System.out.println("  " + j);
            }
        }
    }

    private static Jogador[] criarJogadores() {
        Jogador j1 = new Jogador(1, "Carlos", "senha1", Jogador.Posicao.ATACANTE);
        j1.receberAvaliacao(8.5);
        j1.receberAvaliacao(9.0);

        Jogador j2 = new Jogador(2, "Fernanda", "senha2", Jogador.Posicao.GOLEIRO);
        j2.receberAvaliacao(7.0);
        j2.receberAvaliacao(7.5);

        Jogador j3 = new Jogador(3, "Rodrigo", "senha3", Jogador.Posicao.DEFENSOR);
        j3.receberAvaliacao(6.0);

        return new Jogador[]{j1, j2, j3};
    }
}
