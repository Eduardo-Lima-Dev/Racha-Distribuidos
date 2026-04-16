package testes;

import models.Jogador;
import streams.JogadorOutputStream;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Testa JogadorOutputStream com dois destinos locais:
 *   1. System.out  (saída padrão — bytes brutos)
 *   2. FileOutputStream (arquivo jogadores.bin)
 *
 * O teste com servidor remoto (TCP) será feito na Fase 4.
 */
public class TesteStreamSaida {

    public static void main(String[] args) throws IOException {
        Jogador[] jogadores = criarJogadores();

        testarSaidaPadrao(jogadores);
        testarArquivo(jogadores);

        System.err.println("\n[OK] Testes de JogadorOutputStream concluídos.");
    }

    // ---------------------------------------------------------------
    // Teste 1: System.out
    // ---------------------------------------------------------------
    private static void testarSaidaPadrao(Jogador[] jogadores) throws IOException {
        System.err.println("=== Teste 1: Escrita em System.out (bytes brutos) ===");

        // System.out é um OutputStream válido — os bytes serializados aparecem no terminal
        JogadorOutputStream jos = new JogadorOutputStream(jogadores, jogadores.length, System.out);
        jos.enviar();
        jos.flush();
        // Não fechamos System.out para não quebrar saídas futuras
        System.err.println("\n[OK] Bytes escritos em System.out");
    }

    // ---------------------------------------------------------------
    // Teste 2: FileOutputStream
    // ---------------------------------------------------------------
    private static void testarArquivo(Jogador[] jogadores) throws IOException {
        System.err.println("\n=== Teste 2: Escrita em arquivo jogadores.bin ===");

        try (FileOutputStream fos = new FileOutputStream("jogadores.bin");
             JogadorOutputStream jos = new JogadorOutputStream(jogadores, jogadores.length, fos)) {

            jos.enviar();
        }

        System.err.println("[OK] Arquivo jogadores.bin criado com " + jogadores.length + " jogadores.");
    }

    // ---------------------------------------------------------------
    // Dados de teste
    // ---------------------------------------------------------------
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
