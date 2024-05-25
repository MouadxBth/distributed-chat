package me.mouad.api;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Server extends Remote {

    void register(String username, Client client) throws RemoteException;
    void unregister(String username) throws RemoteException;
    void broadcastMessage(String sender, String message) throws RemoteException;
    void broadcastFile(String sender, byte[] fileData, String fileName) throws RemoteException;
}
