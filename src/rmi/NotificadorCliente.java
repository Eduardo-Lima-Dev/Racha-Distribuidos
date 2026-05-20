package rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NotificadorCliente extends Remote {
    void notificar(byte[] payload) throws RemoteException;
}
