package rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServicoRacha extends Remote {
    byte[] invocar(byte[] request) throws RemoteException;
}
