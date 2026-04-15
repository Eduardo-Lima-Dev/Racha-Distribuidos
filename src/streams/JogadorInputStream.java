package streams;

import models.Jogador;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Stream de entrada customizado que desserializa bytes gerados por
 * JogadorOutputStream e reconstrói o array de Jogadores original.
 *
 * Respeita o mesmo protocolo binário de JogadorOutputStream:
 * ┌──────────────────────────────────────────────────────────┐
 * │  [int: quantidade de objetos]                            │
 * │  Para cada objeto:                                       │
 * │    [int:    tamanhoEmBytes]                              │
 * │    [int:    id]                                          │
 * │    [UTF:    nome]                                        │
 * │    [UTF:    posicao]                                     │
 * │    [double: somaNotas]                                   │
 * │    [int:    qtdNotas]                                    │
 * └──────────────────────────────────────────────────────────┘
 */
public class JogadorInputStream extends InputStream {

    private final DataInputStream origem;

    /**
     * @param origem InputStream de origem (System.in, FileInputStream, Socket, etc.)
     */
    public JogadorInputStream(InputStream origem) {
        if (origem == null) throw new IllegalArgumentException("InputStream de origem não pode ser null");
        this.origem = new DataInputStream(origem);
    }

    /**
     * Lê o stream completo e retorna o array de Jogadores desserializados.
     *
     * @return array de Jogadores lidos do stream
     */
    public Jogador[] receber() throws IOException {
        int quantidade = origem.readInt();
        Jogador[] jogadores = new Jogador[quantidade];

        for (int i = 0; i < quantidade; i++) {
            jogadores[i] = lerJogador();
        }

        return jogadores;
    }

    /**
     * Lê um único Jogador do stream respeitando o length-prefix.
     */
    private Jogador lerJogador() throws IOException {
        // Lê e descarta o tamanho (já sabemos o que esperar pelo protocolo)
        int tamanhoEmBytes = origem.readInt();
        // Leitura dos atributos na mesma ordem em que foram escritos
        int id = origem.readInt();
        String nome = origem.readUTF();
        Jogador.Posicao posicao = Jogador.Posicao.valueOf(origem.readUTF());
        double somaNotas = origem.readDouble();
        int qtdNotas = origem.readInt();

        Jogador j = new Jogador();
        j.setId(id);
        j.setNome(nome);
        j.setPosicao(posicao);
        j.setSomaNotas(somaNotas);
        j.setQtdNotas(qtdNotas);

        return j;
    }

    // --- Implementação obrigatória de InputStream ---

    /** Lê um único byte do stream de origem. */
    @Override
    public int read() throws IOException {
        return origem.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return origem.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
        origem.close();
    }
}
