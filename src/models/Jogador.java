package models;

/**
 * POJO que representa um jogador participante do sistema de divisão de times.
 * Implementa Avaliavel pois pode receber notas de outros jogadores.
 *
 * Atributos serializados nos streams:
 *   - id         (int,    4 bytes)
 *   - nome       (UTF,    2 bytes de tamanho + bytes do texto)
 *   - posicao    (UTF,    2 bytes de tamanho + bytes do texto)
 *   - somaNotas  (double, 8 bytes)
 *   - qtdNotas   (int,    4 bytes)
 */
public class Jogador extends Usuario implements Avaliavel {

    public enum Posicao {
        GOLEIRO, DEFENSOR, MEIO_CAMPO, ATACANTE
    }

    private Posicao posicao;
    private double somaNotas;
    private int qtdNotas;

    public Jogador() {}

    public Jogador(int id, String nome, String senha, Posicao posicao) {
        super(id, nome, senha);
        this.posicao = posicao;
        this.somaNotas = 0.0;
        this.qtdNotas = 0;
    }

    @Override
    public double getNotaMedia() {
        if (qtdNotas == 0) return 0.0;
        return somaNotas / qtdNotas;
    }

    @Override
    public void receberAvaliacao(double nota) {
        if (nota < 0.0 || nota > 10.0) {
            throw new IllegalArgumentException("Nota deve estar entre 0.0 e 10.0, recebido: " + nota);
        }
        somaNotas += nota;
        qtdNotas++;
    }

    public Posicao getPosicao() { return posicao; }
    public void setPosicao(Posicao posicao) { this.posicao = posicao; }

    public double getSomaNotas() { return somaNotas; }
    public void setSomaNotas(double somaNotas) { this.somaNotas = somaNotas; }

    public int getQtdNotas() { return qtdNotas; }
    public void setQtdNotas(int qtdNotas) { this.qtdNotas = qtdNotas; }

    @Override
    public String toString() {
        return String.format("Jogador{id=%d, nome='%s', posicao=%s, media=%.2f}",
                getId(), getNome(), posicao, getNotaMedia());
    }
}
