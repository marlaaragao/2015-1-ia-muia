package application;

import java.net.UnknownHostException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import common.Logger;
import common.SerializableHandler;
import application.exceptions.UnableToCreateMUIAException;
import application.interfaces.CopyMUIAObservable;
import application.interfaces.CopyMUIAObserver;


public class CopyMUIA extends MUIA implements CopyMUIAObserver {
	/**
	 * Unique serial long used to identify the class in the serialization and deserialization.
	 */
	private static final long serialVersionUID = 111333453657765955L;

	/**
	 * Remote Reference of the original MUIA, only used by copy MUIAs.
	 */
	protected CopyMUIAObservable remoteOriginalMUIA;
	
	/**
	 * Creates a new instance of the CopyMUIA class.
	 * @param name - String containing the name of the MUIA.
	 * @param address - String containing the IP address of the MUIA.
	 * @param port - Integer containing the port where the MUIA is running.
	 * @param registryPort - Integer containing the port of registry where the MUIA is binded.
	 * @throws UnknownHostException when the address parameter is not found.
	 * @throws UnableToCreateMUIAException when is not possible to create the remote reference of MUIA.
	 */
	public CopyMUIA(String name, String address, Integer port, Integer registryPort) throws UnknownHostException,
			UnableToCreateMUIAException {
		super(name, address, port, registryPort);
		
		MUIAChecker checker = new MUIAChecker(this);
		Thread tchecker = new Thread(checker);
		tchecker.start();
		
		try {
			synchronizeCopyToOriginalMUIA();
		} catch (RemoteException | NotBoundException e) {
			Logger.error("Unable to synchronyze MUIA copy {" + this + "} with your real MUIA");
		}
	}
	
	/**
	 * Establishes the connection of copy MUIA with your original MUIA and synchronize it by the subscription of the
	 * copy in the list of observers of the original MUIA. 
	 * @throws RemoteException while getting the original MUIA registry, while getting original MUIA remote reference
	 * in the registry or while adding the copy in the observer list of the original MUIA.
	 * @throws NotBoundException when the original MUIA is not founded in the registry.
	 */
	public void synchronizeCopyToOriginalMUIA() throws RemoteException, NotBoundException {
		clients.clear();
		
		Logger.info("Subscribing local MUIA copy {" + this + "} in the observer list of the real MUIA...");
		Registry registry = LocateRegistry.getRegistry(this.address.getHostAddress(), this.registryPort);
		remoteOriginalMUIA = (CopyMUIAObservable) registry.lookup(this.name);
		remoteOriginalMUIA.addCopyMUIAObserver(((CopyMUIAObserver) selfRemoteReference));
		alive = true;
	}
	
	/**
	 * Verifies if the connection with the original MUIA is alive.
	 * If the original MUIA connection is not alive and the last check is alive, the copy will set yourself to not
	 * alive and release the original MUIA reference.
	 * If the original MUIA connection is alive and the last check is not alive, the copy will synchronize with the
	 * original MUIA.
	 */
	public void keepAlive() {
		Boolean isAlive;
		try {
			isAlive = remoteOriginalMUIA.isAlive();
		} catch (Exception e) {
			isAlive = false;
		}
		
		if( isAlive && alive == true ) {
			return;
		} else if( !isAlive && alive == true ) {
			alive = false;
			remoteOriginalMUIA = null;
		}
		
		if( alive == false && remoteOriginalMUIA == null ) {
			try {
				synchronizeCopyToOriginalMUIA();
			} catch (RemoteException | NotBoundException e) {
				Logger.error("Unable to synchronyze MUIA copy {" + this + "} with your real MUIA");
			}
		}
	}
	
	@Override
	public void updateClientAddition(byte[] serializedClient) {
		SerializableHandler<Client> sh = new SerializableHandler<Client>();
		Client client = sh.deserialize(serializedClient);
		addClient(client);
	}

	@Override
	public void updateClientRemoval(String clientName) {
		for( Client client : clients ) {
			if( client.getName().equals(clientName) ) {
				removeClient(client);
				break;
			}
		}
	}
}
