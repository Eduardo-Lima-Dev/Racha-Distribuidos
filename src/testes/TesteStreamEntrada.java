package testes;

import models.Jogador;
import streams.JogadorInputStream;
import streams.JogadorOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Testa JogadorInputStream com duas origens locais:
 *   1. ByteArrayInputStream  (simula System.in sem necessidade de digitação manual)
 *   2. FileInputStream       (lê o arquivo jogadores.bin gerado por TesteStreamSaida)
 *
 * O teste com servidor remoto (TCP) será feito na Fase 4.
 */
public class TesteStreamEntrada {

    public static void main(String[] args) throws IOException {
        testarByteArray();
        testarArquivo();

        System.out.println("\n[OK] Testes de JogadorInputStream concluídos.");
    }

    // ---------------------------------------------------------------
    // Teste 1: ByteArrayInputStream (simula System.in)
    // ---------------------------------------------------------------
    private static void testarByteArray() throws IOException {
        System.out.println("=== Teste 1: Leitura de ByteArrayInputStream (simula System.in) ===");

        // Primeiro serializa em memória
        Jogador[] originais = criarJogadores();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JogadorOutputStream jos = new JogadorOutputStream(originais, originais.length, baos);
        jos.enviar();

        // Agora lê de volta como se fosse System.in
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        JogadorInputStream jis = new JogadorInputStream(bais);
        Jogador[] lidos = jis.receber();

        System.out.println("Jogadores lidos (" + lidos.length + "):");
        for (Jogador j : lidos) {
            System.out.println("  " + j);
        }
        verificar(originais, lidos);
    }

    // ---------------------------------------------------------------
    // Teste 2: FileInputStream — lê jogadores.bin
    // ---------------------------------------------------------------
    private static void testarArquivo() throws IOException {
        System.out.println("\n=== Teste 2: Leitura de FileInputStream (jogadores.bin) ===");

        try (FileInputStream fis = new FileInputStream("jogadores.bin");
             JogadorInputStream jis = new JogadorInputStream(fis)) {

            Jogador[] lidos = jis.receber();

            System.out.println("Jogadores lidos (" + lidos.length + "):");
            for (Jogador j : lidos) {
                System.out.println("  " + j);
            }
        }
    }

    // ---------------------------------------------------------------
    // Verificação de integridade
    // ---------------------------------------------------------------
    private static void verificar(Jogador[] originais, Jogador[] lidos) {
        if (originais.length != lidos.length) {
            System.err.println("[FALHA] Quantidade diferente: esperado "
                    + originais.length + ", obtido " + lidos.length);
            return;
        }
        for (int i = 0; i < originais.length; i++) {
            boolean ok = originais[i].getId() == lidos[i].getId()
                    && originais[i].getNome().equals(lidos[i].getNome())
                    && originais[i].getPosicao() == lidos[i].getPosicao()
                    && Double.compare(originais[i].getSomaNotas(), lidos[i].getSomaNotas()) == 0
                    && originais[i].getQtdNotas() == lidos[i].getQtdNotas();
            System.out.println("  Jogador[" + i + "] " + (ok ? "[OK]" : "[FALHA]")
                    + " id=" + lidos[i].getId() + " nome='" + lidos[i].getNome() + "'");
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
