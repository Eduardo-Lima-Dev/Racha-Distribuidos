package streams;

import models.Jogador;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Stream de saída customizado que serializa um array de Jogadores em bytes
 * e os envia para um OutputStream de destino.
 *
 * Protocolo binário:
 * ┌──────────────────────────────────────────────────────────┐
 * │  [int: quantidade de objetos]                            │
 * │  Para cada objeto:                                       │
 * │    [int:    tamanhoEmBytes] ← bytes dos atributos abaixo │
 * │    [int:    id]              4 bytes                     │
 * │    [UTF:    nome]            2 + n bytes                 │
 * │    [UTF:    posicao]         2 + n bytes                 │
 * │    [double: somaNotas]       8 bytes                     │
 * │    [int:    qtdNotas]        4 bytes                     │
 * └──────────────────────────────────────────────────────────┘
 *
 * O campo tamanhoEmBytes satisfaz o requisito de enviar, para cada objeto,
 * o número de bytes utilizados para gravar pelo menos 3 atributos.
 */
public class JogadorOutputStream extends OutputStream {

    private final DataOutputStream destino;
    private final Jogador[] jogadores;
    private final int quantidade;

    /**
     * @param jogadores array de objetos a serem transmitidos
     * @param quantidade número de objetos que terão dados enviados
     * @param destino OutputStream de destino (System.out, FileOutputStream, Socket, etc.)
     */
    public JogadorOutputStream(Jogador[] jogadores, int quantidade, OutputStream destino) {
        if (jogadores == null) throw new IllegalArgumentException("Array de jogadores não pode ser null");
        if (quantidade < 0 || quantidade > jogadores.length)
            throw new IllegalArgumentException("Quantidade inválida: " + quantidade);

        this.jogadores = jogadores;
        this.quantidade = quantidade;
        this.destino = new DataOutputStream(destino);
    }

    public void enviar() throws IOException {
        destino.writeInt(quantidade);

        for (int i = 0; i < quantidade; i++) {
            escreverJogador(jogadores[i]);
        }

        flush();
    }

    private void escreverJogador(Jogador j) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream bufferDos = new DataOutputStream(buffer);

        bufferDos.writeInt(j.getId());
        bufferDos.writeUTF(j.getNome());
        bufferDos.writeUTF(j.getPosicao().name());
        bufferDos.writeDouble(j.getSomaNotas());
        bufferDos.writeInt(j.getQtdNotas());
        bufferDos.flush();

        byte[] dados = buffer.toByteArray();

        destino.writeInt(dados.length);
        destino.write(dados);
    }

    @Override
    public void write(int b) throws IOException {
        destino.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        destino.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        destino.flush();
    }

    @Override
    public void close() throws IOException {
        destino.close();
    }
}
