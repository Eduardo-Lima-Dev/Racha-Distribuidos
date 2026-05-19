package client;

import rmi.NotificadorCliente;
import utils.Json;

import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Map;

/**
 * Callback do cliente: o servidor invoca {@link #notificar(byte[])} sempre
 * que ha um aviso ou um conjunto de times para divulgar.
 *
 * Substitui o ClienteMulticast UDP do Trabalho 1. Exibe a notificacao em
 * uma linha extra do terminal sem interromper a interacao em curso.
 *
 * Tipos de payload (JSON):
 *   {"tipo":"AVISO","de":..,"mensagem":..,"hora":..}
 *   {"tipo":"TIMES","de":..,"mensagem":"<json-racha>","hora":..}
 */
public class NotificadorImpl extends UnicastRemoteObject implements NotificadorCliente {

    public NotificadorImpl() throws RemoteException {
        super();
    }

    @Override
    public void notificar(byte[] payload) throws RemoteException {
        try {
            String json = new String(payload, StandardCharsets.UTF_8);
            Map<String, Object> m = Json.parseObj(json);
            String tipo     = Json.getString(m, "tipo", "AVISO");
            String de       = Json.getString(m, "de", "?");
            String mensagem = Json.getString(m, "mensagem", "");
            String hora     = Json.getString(m, "hora", "");

            if ("TIMES".equals(tipo)) {
                imprimirTimes(de, hora, mensagem);
            } else {
                imprimirAviso(de, hora, mensagem);
            }
        } catch (Exception e) {
            System.err.println("[NOTIF] Falha ao processar notificacao: " + e.getMessage());
        }
    }

    private static void imprimirAviso(String de, String hora, String mensagem) {
        System.out.println();
        System.out.println("┌─ AVISO [" + hora + "] de " + de + " ─────────");
        System.out.println("│ " + mensagem);
        System.out.println("└─────────────────────────────────────");
        System.out.print("> ");
        System.out.flush();
    }

    @SuppressWarnings("unchecked")
    private static void imprimirTimes(String de, String hora, String rachaJson) {
        System.out.println();
        System.out.println("┌─ TIMES GERADOS [" + hora + "] ─────────");
        try {
            Map<String, Object> racha = Json.parseObj(rachaJson);
            int qtdTimes = Json.getInt(racha, "qtdTimes");
            System.out.printf("│ Racha #%s | %d times | media geral=%.2f%n",
                    String.valueOf(racha.get("id")), qtdTimes, Json.getDouble(racha, "mediaGeral"));
            List<Object> times = Json.getArr(racha, "times");
            for (Object t : times) {
                Map<String, Object> tm = (Map<String, Object>) t;
                System.out.printf("│  Time %s (media=%.2f)%n",
                        String.valueOf(tm.get("numero")), Json.getDouble(tm, "media"));
                List<Object> membros = Json.getArr(tm, "jogadores");
                for (Object mObj : membros) {
                    Map<String, Object> j = (Map<String, Object>) mObj;
                    System.out.printf("│    - %-14s %-12s media=%.2f%n",
                            j.get("nome"), j.get("posicao"), Json.getDouble(j, "media"));
                }
            }
        } catch (Exception e) {
            System.out.println("│ " + rachaJson);
        }
        System.out.println("└─────────────────────────────────────");
        System.out.print("> ");
        System.out.flush();
    }
}
