package rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface SessaoRemota extends Remote {

    byte[] invocar(byte[] request) throws RemoteException;

    /** Registra um callback do cliente — passagem por referência de objeto remoto. */
    void registrarNotificador(NotificadorCliente notificador) throws RemoteException;
}
