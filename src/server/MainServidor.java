package server;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Entry-point do servidor RMI.
 *
 * Cria (ou reutiliza) o Registry na porta 1099, instancia EstadoServidor
 * (carrega dados iniciais) e exporta o ServicoRachaImpl sob o nome
 * "ServicoRacha". Cada login posterior exporta uma SessaoRemotaImpl
 * adicional sob "Sessao:&lt;id&gt;".
 */
public class MainServidor {

    public static final int PORTA_REGISTRY = 1099;
    public static final String NOME_SERVICO = "ServicoRacha";

    public static void main(String[] args) {
        int porta = PORTA_REGISTRY;
        if (args.length > 0) {
            try { porta = Integer.parseInt(args[0]); }
            catch (NumberFormatException ignored) {
                System.err.println("Porta invalida; usando " + PORTA_REGISTRY);
            }
        }

        try {
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(porta);
                System.out.println("[RMI] Registry criado na porta " + porta);
            } catch (Exception e) {
                registry = LocateRegistry.getRegistry(porta);
                System.out.println("[RMI] Registry reutilizado na porta " + porta);
            }

            EstadoServidor estado = new EstadoServidor();
            ServicoRachaImpl servico = new ServicoRachaImpl(estado, registry);
            registry.rebind(NOME_SERVICO, servico);

            System.out.println("============================================");
            System.out.println("  Servidor RMI - Divisao de Times (Trab 2)  ");
            System.out.println("============================================");
            System.out.println("Servico       : " + NOME_SERVICO);
            System.out.println("Porta Registry: " + porta);
            System.out.println("Jogadores     : " + estado.jogadores.size());
            System.out.println("Admins        : " + estado.admins.size());
            System.out.println("Aguardando invocacoes remotas...");
            System.out.println();

            // Mantem o processo vivo (UnicastRemoteObject ja cria threads para servir chamadas)
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("[ERRO] Falha ao iniciar servidor: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
