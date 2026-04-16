package models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * POJO que representa um time gerado pelo servidor após o encerramento
 * do período de avaliações. Um time é imutável após sua criação.
 */
public class Time {

    private int numero;
    private List<Jogador> jogadores;

    public Time() {
        this.jogadores = new ArrayList<>();
    }

    public Time(int numero) {
        this.numero = numero;
        this.jogadores = new ArrayList<>();
    }

    public void adicionarJogador(Jogador jogador) {
        jogadores.add(jogador);
    }

    /**
     * Calcula a média de habilidade do time somando as médias individuais.
     */
    public double getMediaDoTime() {
        if (jogadores.isEmpty()) return 0.0;
        double soma = 0.0;
        for (Jogador j : jogadores) {
            soma += j.getNotaMedia();
        }
        return soma / jogadores.size();
    }

    public int getNumero() { return numero; }
    public void setNumero(int numero) { this.numero = numero; }

    public List<Jogador> getJogadores() {
        return Collections.unmodifiableList(jogadores);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Time ").append(numero).append(" (media=")
          .append(String.format("%.2f", getMediaDoTime())).append("):\n");
        for (Jogador j : jogadores) {
            sb.append("  - ").append(j).append("\n");
        }
        return sb.toString();
    }
}
