package rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface SessaoRemota extends Remote {
    byte[] invocar(byte[] request) throws RemoteException;
}
