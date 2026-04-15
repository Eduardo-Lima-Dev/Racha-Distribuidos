package models;

/**
 * Interface que define o contrato para entidades que podem ser avaliadas
 * numericamente no sistema de divisão de times.
 */
public interface Avaliavel {

    /**
     * Retorna a nota média de avaliação da entidade.
     * O intervalo esperado é de 0.0 a 10.0.
     */
    double getNotaMedia();

    /**
     * Registra uma nova avaliação para a entidade.
     *
     * @param nota valor entre 0.0 e 10.0
     */
    void receberAvaliacao(double nota);
}
