package me.mouad.api;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface Client extends Remote {
    void receiveMessage(String sender, String message) throws RemoteException;
    void receiveFile(String sender, byte[] fileData, String fileName) throws RemoteException;
    void updateConnectedUsersList(List<String> users) throws RemoteException;
}
