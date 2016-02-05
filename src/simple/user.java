package simple;

import java.net.Socket;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

public class user {
    private static final int USER_THROTTLE = 200;
    private Socket socket;
    private boolean connected;
    private Inport inport;
    private ObjectInputStream in;
    private ObjectOutputStream out;
    private String Username;
    
    // JDBC driver name and database URL
    static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";  
    static final String DB_URL = "jdbc:mysql://localhost/";
    //  Database credentials
    static final String USER = "root";
    static final String PASS = "password";
    Statement stmt = null;
    
    //Handles all incoming data from this user.
    private class Inport extends Thread
    {
        public void run()
        {
            //Clear connection
            Connection conn = null;
            try{
                //Register JDBC driver
                Class.forName("com.mysql.jdbc.Driver");

                //Open a connection
                conn = DriverManager.getConnection(DB_URL, USER, PASS);

                //Execute a query
                stmt = conn.createStatement();

                String sql = "CREATE DATABASE IF NOT EXISTS SIMPLEMESSENGERDB";
                stmt.executeUpdate(sql);

                //Connect to Database
                conn = DriverManager.getConnection(DB_URL + "SIMPLEMESSENGERDB", USER, PASS);
                stmt = conn.createStatement();

                //Create table to store messages
                sql = "CREATE TABLE IF NOT EXISTS Chat (" +
                       "Type VARCHAR(18), " +
                       "Sender VARCHAR(25), " +
                       "Message VARCHAR(255), " +
                       "Time DATETIME)"; 
                stmt.executeUpdate(sql);

                //Get previous messages
                sql = "select * from chat";
                ResultSet rs = stmt.executeQuery(sql);
                while (rs.next()) {
                  // Retrieve by column name
                  Message dbmsg = new Message(rs.getString("Type"),rs.getString("Sender"), rs.getString("Message"), rs.getString("Time"));
                  // Display values
                  out.writeObject(dbmsg);
                  out.flush();
                }
                rs.close();

             }catch(SQLException se){
                //Handle errors for JDBC
                se.printStackTrace();
             }catch(Exception e){
                //Handle errors for Class.forName
                e.printStackTrace();
             }finally{
                //finally block used to close resources
                try{
                   if(stmt!=null)
                      stmt.close();
                }catch(SQLException se2){
                }// nothing we can do
                try{
                   if(conn!=null)
                      conn.close();
                }catch(SQLException se){
                   se.printStackTrace();
                }//end finally try
             }//end try
            
            try {
                //Connect to Database
                conn = DriverManager.getConnection(DB_URL + "SIMPLEMESSENGERDB", USER, PASS);
                stmt = conn.createStatement();
            } catch (Exception e) {
            }
            
            // Enter process loop
            while(true)
            {
                String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime());
                try {
                    //Read message sent by user
                    Message m = (Message)(in.readObject());
                    //Insert message into database (message and time of message)
                    String sql = "INSERT INTO chat " +
                        "VALUES ('"+m.type+"','"+m.sender+"','"+m.content+"','" +timeStamp+"')";
                    stmt.executeUpdate(sql);
                    //Loop through all the users connected to Server
                    if (m.type.equals("message"))
                    {
                        for (user tempuser : simpleserver.getUsers())
                        {
                            //Send message to user (who sent it and what they said)
                            tempuser.out.writeObject(m);
                            tempuser.out.flush();
                        }
                    }
                    else if (m.type.equals("connecting"))
                    {
                        //Set username for current user
                        Username = m.sender;
                        //Display already connected users to the new user
//                        for (user tempuser : simpleserver.getUsers())
//                        {
//                            Message x = new Message("connecting", tempuser.Username, tempuser.Username, timeStamp);
//                            out.writeObject(x);
//                            out.flush(); 
//                        }
                        //Display new user to already connected users
                        for (user tempuser : simpleserver.getUsers())
                        {
                            //if (!tempuser.Username.equals(Username))
                            //{
                                Message x = new Message("connecting", Username, Username, timeStamp);
                                tempuser.out.writeObject(x);
                                tempuser.out.flush();
                            //}
                        }
                    }
                } catch (IOException | ClassNotFoundException e) {
                    System.out.println("Couldn't read/write message " + e);
                    if (e.getMessage().equals("Connection reset"))
                    {
                        //Close connection if user closed client
                        purge();
                        Message z = new Message("disconnected", Username, Username,timeStamp);
                        try {
                            String sql = "INSERT INTO chat " +
                                "VALUES ('"+z.type+"','"+z.sender+"','"+z.content+"','" +timeStamp+"')";
                            stmt.executeUpdate(sql);
                            conn.close();
                        } catch (Exception p) {
                        }
                  
                        for (user tempuser : simpleserver.getUsers())
                        {
                            
                            //Send message to user
                            try {
                                tempuser.out.writeObject(z);
                                tempuser.out.flush();
                            } catch (Exception a) {
                                System.out.println(e);
                            }
                        }
                    }
                    return;
                } catch (SQLException ex) {
                    Logger.getLogger(user.class.getName()).log(Level.SEVERE, null, ex);
                }
                
                //Sleep
                try
                {
                    Thread.sleep(USER_THROTTLE);
                }
                catch(Exception e)
                {
                    System.out.println(toString()+" has input interrupted.");
                }
            }
        }
    }
    /**
     * Creates a new Client User with the socket from the newly connected client.
     *
     * @param newSocket  The socket from the connected client.
     */

    public user(Socket newSocket)
    {
        // Set properties
        socket = newSocket;
        connected = true;
        
        try
        {
            //Open the InputStream
            in = new ObjectInputStream(socket.getInputStream());
        }
        catch(IOException e)
        {
            System.out.println("Could not get input stream from "+toString());
            return;
        }
        
        try
        {
            //Open the OutputStream
            out = new ObjectOutputStream(socket.getOutputStream());
        }
        catch(IOException e)
        {
            System.out.println("Could not get Output stream from "+toString());
            return;
        }
        
        // Get input
        inport = new Inport();
        inport.start();
    }
    /**
     * Gets the connection status of this user.
     *
     * @return  If this user is still connected.
     */
    public boolean isConnected()
    {
        return connected;
    }
    /**
     * Purges this user from connection.
     */
    public void purge()
    {
        // Close everything
        try
        {
                connected = false;
                socket.close();
                //remove user from array
                simpleserver.removeUser();
                return;
        }
        catch(IOException e)
        {
                System.out.println("Could not purge "+socket+".");
        }
    }
    /**
     * Returns the String representation of this user.
     *
     * @return  A string representation.
     */
    public String toString()
    {
        return new String(socket.toString());
    }
}
