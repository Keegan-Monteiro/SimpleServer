package simple;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.ArrayList;

public class simpleserver extends Thread {
    private static final int PORT = 30480;
    private static final int ROOM_THROTTLE = 200;
    private static ServerSocket serverSocket;
    private Socket socket;
    private static ArrayList<user> users = new ArrayList<user>();
    
    public static ArrayList<user> getUsers () 
    {
        //Return all connected users
        return users;
    }
    
    //Removes user if they are disconnected
    public static void removeUser ()
    {
        for(int i = 0;i < users.size();i++)
        {
            //Check connection, remove on dead
            if(!users.get(i).isConnected())
            {
                System.out.println(users.get(i)+" removed due to lack of connection.");
                users.remove(i);
            }
        }
    }
    
    //Creates a new room for clients to connect to.
    public simpleserver()
    {
        //Attempt to create server socket
        try
        {
            serverSocket = new ServerSocket(PORT);
        }
        catch(IOException e)
        {
            System.out.println("Could not open server socket.");
            return;
        }
        //Announce the socket creation
        System.out.println("Socket "+serverSocket+" created.");
    }
   
    //Starts the client accepting process.
    public void run()
    {
        //Announce the starting of the process
        System.out.println("Room has been started.");
        //Enter the main loop
        while(true)
        {
            //Remove all disconnected clients
            removeUser ();
            //Get a client trying to connect
            try
            {
                socket = serverSocket.accept();
            }
            catch(IOException e)
            {
                System.out.println("Could not get a client.");
            }
            //Client has connected
            System.out.println("Client "+socket+" has connected.");
            //Add user to list
            users.add(new user(socket));
            //Sleep
            try
            {
                Thread.sleep(ROOM_THROTTLE);
            }
            catch(InterruptedException e)
            {
                System.out.println("Room has been interrupted.");
            }
        }
    }
    
    public static void main(String[] args)
    {
        simpleserver newserver= new simpleserver();
        newserver.start();
    }
}
