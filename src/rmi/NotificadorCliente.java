package rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NotificadorCliente extends Remote {
    byte[] invocar(byte[] request) throws RemoteException;
}
