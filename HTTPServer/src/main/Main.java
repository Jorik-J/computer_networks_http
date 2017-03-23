package main;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * The server's program class containing the main function.
 * 
 * @author Bauwen Demol (r0583318)
 * @author Jorik Jooken (r0588270)
 */
public class Main {
	
	/**
	 * The main function of the client program.
	 * 
	 * Expects at least 1 command-line argument (the local host path representing the web server) and
	 * optionally a second argument indicating the port number to use.
	 * 
	 * Listens to incoming socket connections and lets a separate thread handle them.
	 * 
	 * @param args
	 * 		The command-line arguments
	 * 
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		
		// get the command line argument
		
		if (args.length < 1) {
			System.out.println("Error: expected 1 argument containing directory in 'files/'.");
			return;
		}
		
		String path = args[0];
		
		int port = 8000;
		
		if (args.length > 1) {
			port = Integer.parseInt(args[1]);
		}
		
		
		// setup the server and listen for incoming connections
		
		ServerSocket serverSocket = new ServerSocket(port);
		System.out.println("Listening on port " + port + "...");
		
		try {
			while (true) {
				Socket socket = serverSocket.accept();
				new Thread(new HttpConnection(socket, path)).start();
			}
		} catch (IOException e) {
			serverSocket.close();
		}
	}
}
