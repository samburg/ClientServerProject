/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package burgessclient;

import javax.xml.crypto.Data;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;
//import burgessclient.BurgessServer;


/**
 *
 * @author admin
 */
public class BurgessClient extends Thread {
    
    public static final int DEFAULT_SERVER_IN_PORT = 13231;//per the assignment
    public static final int DEFAULT_SERVER_OUT_PORT = 13232;//need to use another port because java only lets one socket listen to a given port
    public static final int MAX_PSIZE = 1400;//might not be necessary to have this
    public static final String FAIL_STRING = "fail";//what the client will transmit in case of a failed transmission
    public static final String TRANSMISSION_COMPLETE_STRING = "complete";//the last packet the server will send when the entire file has been transmitted
    public static final String SUCCESS_STRING = "file OK";//what the client will transmit in case of a successful transmission
    public static final String DEFAULT_FILENAME = "Z:\\grad school work\\COSC650\\650project\\src\\test.txt";
    public static final int MAX_FAILS = 1;

    //class/static variables


    //instance variables
    //public int psize;//how big is each packet?
    public boolean running;
    public int num_fails;//how many times has the transmission failed?
    public State state;
    public DatagramSocket dsocket;
    public int psize;
    public int tout;
    public String sname;
    public String filename;
    public byte[] filedata;
    public InetAddress serverAddress;
    public int serverPort;

    //enum for the state variable
    enum State {
        INITIATING_START, HTTP_REQUEST, RECEIVING, RECEIVING_DOUBLE, FINISHED, ECHO//ECHO is a test state
    }
    
    public BurgessClient()
    {
        running = false;
        num_fails = 0;
        state = State.INITIATING_START;
        psize = -1;//this will be setup for real later
        filename = "";//ditto

        //set up the address
        
        try
        {
            serverAddress = InetAddress.getLocalHost();
        }
        catch (UnknownHostException exception)
        {
            System.out.println("Unable to get local host address");
        }
        

        //set up the socket
        try
        {
            //dsocket = new DatagramSocket();//client does not require a port number
            dsocket = new DatagramSocket(DEFAULT_SERVER_OUT_PORT);
            serverPort = DEFAULT_SERVER_IN_PORT;
        }
        catch (SocketException exception)
        {
            System.out.println("SocketException setting up the DatagramSocket: " + exception + exception.getMessage());
        }
    }//end constructor

    /**
     * begins execution of the server logic
     */
    public void run()//overrides Thread's run()
    {
        running = true;

        while (running) {
            switch (state) {
                case INITIATING_START:
                    handleInitiatingStart();
                    break;
                
                case HTTP_REQUEST:
                    //handleHttpRequest();
                    break;
                    
                case RECEIVING:
                    handleReceiving();
                    break;
                    
                case RECEIVING_DOUBLE:
                     handleReceivingDouble();
                     break;

                case FINISHED:
                    running = false;
                    //System.out.println("running set to false");
                    break;

                case ECHO:
                    //listen for a packet and send it back
                    //handleEcho();
                    break;


            }//end switch
        }//end while running

        //clean up
        dsocket.close();
    }//end method run

    
    public static void main(String[] args) {
        BurgessServer bserve = new BurgessServer();
        bserve.start();
        startClient();
    }
    
    public static void startClient()
    {
        BurgessClient client = new BurgessClient();
        client.start();
        try
        {
            client.join();
        }
        catch (Exception exception)
        {
            System.out.println("Exception in client: " + exception + exception.getMessage());
        }

    }//end method startServer
    
    
    public void handleInitiatingStart() {
        Scanner reader = new Scanner(System.in);  // Reading from System.in
        System.out.println("Enter psize, tout, and sname: ");
        //psize = reader.nextInt();
        //tout = reader.nextInt();// Scans the next token of the input as an int.
        //sname = reader.nextLine();
        String input = reader.nextLine();
        String[] lines = input.split(" ");
        psize = Integer.parseInt(lines[0]);
        tout = Integer.parseInt(lines[1]);
        sname = lines[2];
        System.out.println(psize + " " + tout + " " + sname);
        
        //once finished
        reader.close();
        
        //byte[] buffer = new byte[psize];//we don't know how big the first packet will be
        //DatagramPacket firstPacket = new DatagramPacket(buffer, buffer.length, serverAddress, serverPort);

        try
        {
            byte[] sendData = new byte[1400];
            String sendString = psize + " " + sname;
            sendData = sendString.getBytes();
            
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
            dsocket.send(sendPacket);
            
            //dsocket.send(firstPacket);

            state = State.RECEIVING;//prepare to receive file 
            //state=State.HTTP_REQUEST;//send HTTP request
        }
        catch (Exception exception)
        {
            System.out.println("Error in handleWaitingForStart: " + exception.toString() + " " + exception.getMessage());
        }
    }
    
    /*public void handleHttpRequest(){
        String numberBytes;
        try 
        { InetAddress adr=InetAddress.getByName(sname);
          Socket clientSocket = new Socket(adr, 80);//socket connection based on user input
          long startTime = System.currentTimeMillis(); //start timer for total transmit time 
          BufferedWriter wr= new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF8"));
          wr.write("GET / HTTP/1.1");
          wr.write("Host:" + adr);
          wr.flush();
          BufferedReader rd= new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));   
          String line;
          numberBytes= "";
          while (!(line = rd.readLine()).equals(" ")) {
            if (line.contains("Content-Length")){//find content length in response header, which would be equal to number of bytes in response, set this to own variable
                 numberBytes=line;
            }
            System.out.println(line);
                        
        }
          
          rd.close();
          clientSocket.close();
          long totalTime= System.currentTimeMillis() - startTime;//total transit time
          
          System.out.println("Name of Web Server: " + sname);
          System.out.println("Number of bytes: " + numberBytes);
          System.out.println("Total time from request to complete response: " +totalTime);       
          
    }
        
        
       catch(Exception exception)
        {
            System.out.println("Error in handleHttpRequest: " + exception + exception.getMessage());
        } 
        
    } 
    */
    public void handleReceiving() {
        String numberPacket = "bad1";
        String dataPacket = "bad2";
        try
        {
            
            dsocket.setSoTimeout(tout);//sets timeout in milliseconds specified by user
            byte[] ldata = new byte[psize];//psize is too large but whatever
            DatagramPacket lpacket = new DatagramPacket(ldata, ldata.length);
            dsocket.receive(lpacket);//receive packet from server
            ldata = lpacket.getData();  
            String lstr = new String(ldata).trim();
            numberPacket= lstr.substring(0,4);//since first four bytes are packet number, take first four bytes
            int packetNumber= Integer.parseInt(numberPacket);//convert string to int for Packet Number 
            dataPacket= lstr.substring(4);//get rest of packet
            System.out.println("test:" + numberPacket + " " + dataPacket);
            SortedMap< Integer, String> packetMap= Collections.synchronizedSortedMap(new TreeMap<Integer, String>());//incase map is being modified by more than one thread uses lock to synchronize
            packetMap.put(packetNumber, dataPacket);//put the packet number and associated data into map that will sort based on the key packet value
            System.out.println("Packet Number Received: " + numberPacket);
            
            if (lstr.equals(TRANSMISSION_COMPLETE_STRING))//if data from packet equals "complete", signalling end of file 
            { byte[] filegood = SUCCESS_STRING.getBytes();
              DatagramPacket filepacket = new DatagramPacket(filegood, filegood.length, serverAddress, serverPort);
              dsocket.send(filepacket);//sends contents of success string to server 
              for (Map.Entry<Integer, String> entry: packetMap.entrySet()){
                System.out.println(entry.getValue());//print data from packet based on packet value 
            }
   
              state=State.FINISHED; // packets will have been printed in order 
            }
            
            if (lstr.equals(FAIL_STRING))
            { byte[] filebad = FAIL_STRING.getBytes();
              DatagramPacket filepacket = new DatagramPacket(filebad, filebad.length, serverAddress, serverPort);
              dsocket.send(filepacket);//sends contents of failed string to server
              
              state=State.RECEIVING_DOUBLE;//doubles tout time 
            }
         
        }//end try
        catch (Exception exception)
        {
           if (exception instanceof SocketTimeoutException)
               state=State.RECEIVING_DOUBLE;
           else
            System.out.println("Exception in handleReceiving: " + exception.toString() + " " + exception.getMessage() + " " + numberPacket + dataPacket);
        }//end catch

        
    }
    
    
    public void handleReceivingDouble() {

        try
        {
            int toutdouble=tout*2;
            dsocket.setSoTimeout(toutdouble);//sets timeout in milliseconds specified by user, but doubled
            byte[] ldata = new byte[psize];//psize is too large but whatever
            DatagramPacket lpacket = new DatagramPacket(ldata, ldata.length);
            dsocket.receive(lpacket);//receive packet from server
            ldata = lpacket.getData();  
            String lstr = new String(ldata).trim();
            String numberPacket= lstr.substring(0,4);//since first four bytes are packet number, take first four bytes 
            int packetNumber= Integer.parseInt(numberPacket);//convert string to int for Packet Number 
            String dataPacket= lstr.substring(4);//get rest of packet
            SortedMap< Integer, String> packetMap= Collections.synchronizedSortedMap(new TreeMap<Integer, String>());//incase map is being modified by more than one thread uses lock to synchronize
            packetMap.put(packetNumber, dataPacket);//put the packet number and associated data into map that will sort based on the key packet value
            System.out.println("Packet Number Received: " + numberPacket);
            
            if (lstr.equals(TRANSMISSION_COMPLETE_STRING))//if data from packet equals "complete", signalling end of file 
            { byte[] filegood = SUCCESS_STRING.getBytes();
              DatagramPacket filepacket = new DatagramPacket(filegood, filegood.length, serverAddress, serverPort);
              dsocket.send(filepacket);//sends contents of success string to server 
            for (Map.Entry<Integer, String> entry: packetMap.entrySet()){
                System.out.println(entry.getValue());//print data from packet based on packet value 
            }
              
              
              state=State.FINISHED; // packets will have been printed in order 
            }
            else  {
              System.out.println("quit");//prints quit if unsuccesfully transmitted a second time 
            }
         
        }//end try
        catch (Exception exception)
        {
            System.out.println("Exception in handleReceivingDouble: " + exception.toString() + " " + exception.getMessage());
        }//end catch

  
    }
    
    
    
    
    
    public  void handleEcho()
    {
        byte[] buffer = new byte[112];
        DatagramPacket inpacket = new DatagramPacket(buffer, 112);

        try
        {
            dsocket.receive(inpacket);
        }
        catch (IOException exception)
        {
            System.out.println("Exception receiving packet in ECHO");
        }

        byte[] data = inpacket.getData();
        DatagramPacket outpacket = new DatagramPacket(data, data.length, inpacket.getAddress(), inpacket.getPort());

        try
        {
            dsocket.send(outpacket);
        }
        catch (IOException exception) {
            System.out.println("Exception sending packet in ECHO");
        }
        state = State.FINISHED;
    }//end method handleEcho
    
    
    
    
    
    
    public static int bytesToInt(byte[] ar)
    {
        return ByteBuffer.wrap(ar).getInt();
    }//end method bytestoint

    /**
     * @param i an integer
     * @return i in the form of a byte array
     */
    public static byte[] intToBytes(int i)
    {
        ByteBuffer temp = ByteBuffer.allocate(4);//ints are 4 bytes in java
        temp.putInt(i);
        return temp.array();
    }//end method intToBytes

    /**
     *
     * @param a the first byte array
     * @param b the second byte array
     * @return the concatenation of a and b
     */
    public static byte[] concatBytes(byte[] a, byte[] b)
    {
        byte[] c = new byte[a.length + b.length];

        for (int ai = 0; ai < a.length; ai++)
        {
            c[ai] = a[ai];
            //System.out.println("Inserting " + a[ai] + " at index " + ai);
        }//end first for loop

        int ci = a.length;

        for (int bi = 0; bi < b.length; bi++)
        {
            c[ci] = b[bi];
            ci += 1;
            //System.out.println("Inserting " + b[bi] + " at index " + ci);
        }//end second for loop

        return c;
    }//end method concatBytes
    
    public static byte[] subBytes(byte[] ar, int start, int end)
    {
        byte[] sub = new byte[end - start];

        int si = 0;

        for (int ari = start; ari < end; ari++)
        {
            sub[si] = ar[ari];
            si += 1;
        }//end for ari

        return sub;
    }//end method subBytes
    
}
