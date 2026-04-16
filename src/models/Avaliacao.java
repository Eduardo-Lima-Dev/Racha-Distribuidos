package models;

/**
 * POJO que representa uma avaliação emitida por um jogador sobre outro.
 *
 * Atributos serializados nos streams:
 *   - idAvaliador  (int,    4 bytes)
 *   - idAvaliado   (int,    4 bytes)
 *   - nota         (double, 8 bytes)
 */
public class Avaliacao {

    private int idAvaliador;
    private int idAvaliado;
    private double nota;

    public Avaliacao() {}

    public Avaliacao(int idAvaliador, int idAvaliado, double nota) {
        if (nota < 0.0 || nota > 10.0) {
            throw new IllegalArgumentException("Nota deve estar entre 0.0 e 10.0");
        }
        this.idAvaliador = idAvaliador;
        this.idAvaliado = idAvaliado;
        this.nota = nota;
    }

    public int getIdAvaliador() { return idAvaliador; }
    public void setIdAvaliador(int idAvaliador) { this.idAvaliador = idAvaliador; }

    public int getIdAvaliado() { return idAvaliado; }
    public void setIdAvaliado(int idAvaliado) { this.idAvaliado = idAvaliado; }

    public double getNota() { return nota; }
    public void setNota(double nota) { this.nota = nota; }

    @Override
    public String toString() {
        return String.format("Avaliacao{avaliador=%d, avaliado=%d, nota=%.1f}",
                idAvaliador, idAvaliado, nota);
    }
}
