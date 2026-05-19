package models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Agrupa os Times gerados num evento de encerramento.
 *
 * Composicao (tem-um): Racha tem-uma lista de Time.
 * Cada Time, por sua vez, tem-uma lista de Jogador (composicao em cadeia).
 */
public class Racha {

    private int id;
    private LocalDateTime data;
    private List<Time> times;

    public Racha() {
        this.times = new ArrayList<>();
    }

    public Racha(int id, List<Time> times) {
        this.id = id;
        this.data = LocalDateTime.now();
        this.times = new ArrayList<>(times);
    }

    public Racha(int id, Time[] times) {
        this(id, new ArrayList<>());
        Collections.addAll(this.times, times);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public LocalDateTime getData() { return data; }
    public void setData(LocalDateTime data) { this.data = data; }

    public List<Time> getTimes() {
        return Collections.unmodifiableList(times);
    }

    public int getQuantidadeTimes() {
        return times.size();
    }

    public double getMediaGeral() {
        if (times.isEmpty()) return 0.0;
        double soma = 0.0;
        for (Time t : times) soma += t.getMediaDoTime();
        return soma / times.size();
    }

    @Override
    public String toString() {
        return String.format("Racha{id=%d, data=%s, times=%d, mediaGeral=%.2f}",
                id, data, times.size(), getMediaGeral());
    }
}
