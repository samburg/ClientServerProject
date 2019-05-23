/*
COSC 650 Fall 2018
Group Project
Group members: Sam Burgess, Andrew Curry, Gretchen Hook-Podhorniak, Maria Rivera, Dharamveer Singh
This file coded by: Andrew Curry

this file handles the server side of the project. receives a setup packet from the client and transmits a test file over
UDP. has a protocol for failed transmission.

This tutorial was used while writing this file: https://www.baeldung.com/udp-in-java
This stack overflow page was very helpful: https://stackoverflow.com/questions/858980/file-to-byte-in-java
 */

//imports
/*
Expects the first packet to be in this format: First 4 bytes are psize in int format, remaining bytes are the file
    name/location in string format.
Transmits each packet in this format: First 4 bytes are the packet number in int format, remaining bytes are file
    contents.
After the last packet has been transmitted, sends a packet with data "complete"
Expects the failed transmission packet to be in this format: packet data is the string "fail"
Expects the successful ack packet to be in this format: packet data is the string "
 */

package burgessclient;

import javax.xml.crypto.Data;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;

public class BurgessServer extends Thread//if servers are threads they wont lock up the system while they wait for packets
{
    //constants
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
    public String filename;
    public byte[] filedata;
    public InetAddress clientAddress;
    public int clientPort;

    //enum for the state variable
    enum State {
        WAITING_FOR_START, TRANSMITTING, WAITING_FOR_ACK, FINISHED, ECHO//ECHO is a test state
    }

    /**
     * a constructor
     */
    public BurgessServer()
    {
        running = false;
        num_fails = 0;
        state = State.WAITING_FOR_START;
        psize = -1;//this will be setup for real later
        filename = "";//ditto

        //set up the address
        /*
        try
        {
            address = InetAddress.getLocalHost();
        }
        catch (UnknownHostException exception)
        {
            System.out.println("Unable to get local host address");
        }
        */

        //set up the socket
        try
        {
            dsocket = new DatagramSocket(DEFAULT_SERVER_IN_PORT);
        }
        catch (SocketException exception)
        {
            System.out.println("SocketException setting up the DatagramSocket");
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
                case WAITING_FOR_START:
                    handleWaitingForStart();
                    break;

                case TRANSMITTING:
                    handleTransmitting();
                    break;

                case WAITING_FOR_ACK:
                    handleWaitingForAck();
                    break;

                case FINISHED:
                    running = false;
                    //System.out.println("running set to false");
                    break;

                case ECHO:
                    //listen for a packet and send it back
                    handleEcho();
                    break;


            }//end switch
        }//end while running

        //clean up
        dsocket.close();
    }//end method run

    /**
     * listens for a packet and returns one with the same data to the sender's address
     */
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

    /**
     * this method listens for the first packet from the client with the parameters
     */
    public void handleWaitingForStart()
    {
        byte[] buffer = new byte[MAX_PSIZE];//we don't know how big the first packet will be
        DatagramPacket firstPacket = new DatagramPacket(buffer, buffer.length);
        System.out.println("made it!!");
        try
        {
            dsocket.receive(firstPacket);

            clientAddress = firstPacket.getAddress();
            clientPort = firstPacket.getPort();

            byte[] psizedata = subBytes(firstPacket.getData(), 0, 4);
            byte[] filenamedata = subBytes(firstPacket.getData(), 4, firstPacket.getData().length);

            psize = bytesToInt(psizedata);
            System.out.println("made it here");
            filename = new String(filenamedata).trim();//bytes -> string -> get rid of spaces
            File file = new File(filename);
            filedata = Files.readAllBytes(file.toPath());//get the bytes from the file

            state = State.TRANSMITTING;//now transmit this file
            System.out.println("handleWaiting finish");
        }
        catch (Exception exception)
        {
            System.out.println("Error in handleWaitingForStart: " + exception.toString() + " " + exception.getMessage());
        }
    }//end method handleWaitingForStart

    /**
     * transmits the file to the client
     */
    public void handleTransmitting()
    {
        int packetNumber = 1;//start the count at 1
        int fileIndex = 0;//which byte are we at in the file?

        try
        {
            System.out.println("filedata.length is " + filedata.length);
            while (fileIndex < filedata.length)//as long as there's more data to transmit
            {
                int currentPacketSize;//bow long will this packet be?

                //if there's a lot left, just transmit psize. if the file is almost done, transmit the rest of it
                if ((filedata.length - fileIndex) >= psize)
                {
                    currentPacketSize = psize - 4;
                }
                else
                {
                    currentPacketSize = (filedata.length - fileIndex);//make sure to leave room for the packet number
                    //System.out.println("\n---------------------------Packet number " + packetNumber + " is too small; size is " + currentPacketSize);
                }

                byte[] pdata = new byte[currentPacketSize + 4];//put back in 4 bytes for the packet number
                byte[] pnumber = intToBytes(packetNumber);
                int dataIndex = 0;//where are we in pdata?

                //the first 4 bytes are the packet number
                for (int i = 0; i < pnumber.length; i++)
                {
                    pdata[dataIndex] = pnumber[i];
                    dataIndex += 1;
                }//end packet number for loop

                for (int k = 0; k < currentPacketSize; k++)//k is how many bytes have been put in this packet
                {
                    pdata[dataIndex] = filedata[fileIndex];
                    dataIndex += 1;
                    fileIndex += 1;
                }//end k for loop

                //at this point the data for the packet should be collected
                DatagramPacket dpacket = new DatagramPacket(pdata, pdata.length, clientAddress, clientPort);
                packetNumber += 1;
                dsocket.send(dpacket);

            }//end filedata left while loop

            //send the last packet
            byte[] lastdata = TRANSMISSION_COMPLETE_STRING.getBytes();
            DatagramPacket lastpacket = new DatagramPacket(lastdata, lastdata.length, clientAddress, clientPort);
            dsocket.send(lastpacket);
        }//end try
        catch (Exception exception)
        {
            System.out.println("Exception in handleTransmitting: " + exception.toString() + " " + exception.getMessage());
        }//end catch

        state = State.WAITING_FOR_ACK;//now wait for the ack
    }//end method handleTransmitting

    /**
     * waits to either receive an ack or a request to retransmit
     */
    public void handleWaitingForAck()
    {
        byte[] ldata = new byte[psize];//psize is too large but whatever
        DatagramPacket lpacket = new DatagramPacket(ldata, ldata.length);

        try
        {
            dsocket.receive(lpacket);
            ldata = lpacket.getData();

            String lstr = new String(ldata).trim();

            if (lstr.equals(SUCCESS_STRING))
            {
                System.out.println(SUCCESS_STRING);
                state = State.FINISHED;
            }
            else if (lstr.equals(FAIL_STRING))
            {
                num_fails += 1;
                if (num_fails > MAX_FAILS)//if the transmission has failed too many times
                {
                    byte[] faildata = FAIL_STRING.getBytes();
                    DatagramPacket fpacket = new DatagramPacket(faildata, faildata.length, clientAddress, clientPort);
                    dsocket.send(fpacket);
                    state = State.FINISHED;
                }
                else//if we can still try again
                {
                    state = State.TRANSMITTING;
                }
            }
        }
        catch(Exception exception)
        {
            System.out.println("Error in handleWaitingForAck: " + exception + exception.getMessage());
        }


    }
    /**
     *
     * @param ar an array of bytes
     * @return the value of ar as an int
     */
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

    /**
     * returns a sub-array of the given array (both should be byte[])
     * @param ar the source array
     * @param start the first index to put into the new array
     * @param end the new array will stop at this index (non-inclusive)
     * @return
     */
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

    //test main method
    public static void main(String[] args)
    {
        startServer();
    }//end main method

    /**
     * sets up a server to get ready for the client
     */
    public static void startServer()
    {
        BurgessServer server = new BurgessServer();
        server.start();
        try
        {
            server.join();
        }
        catch (Exception exception)
        {
            System.out.println("Exception in server: " + exception + exception.getMessage());
        }

    }//end method startServer
    /**
     * runs a test of the server class
     */
    public static void test()
    {
        //first part tests ECHO ----------------------------------------------------------------------------
        BurgessServer server = new BurgessServer();
        server.state = State.ECHO;
        server.start();

        System.out.println("Server started");

        DatagramSocket testsocket;
        String teststr;

        InetAddress address;

        try{
            testsocket = new DatagramSocket(DEFAULT_SERVER_OUT_PORT);//listen to the socket the server will send to
            System.out.println("testsocket set up");
            teststr = "Echo me";
            byte[] testdata = teststr.getBytes();

            address = InetAddress.getLocalHost();

            DatagramPacket testpacket = new DatagramPacket(testdata, testdata.length, address, DEFAULT_SERVER_IN_PORT);
            testsocket.send(testpacket);

            //get the packet echo'd by the server
            byte[] listendata = teststr.getBytes();
            DatagramPacket listenpacket = new DatagramPacket(listendata, listendata.length);
            testsocket.receive(listenpacket);

            String listenstr = new String(listenpacket.getData());

            System.out.println(listenstr);
            testsocket.close();

        }
        catch (Exception exception)
        {
            System.out.println("Exception in main: " + exception.getMessage());
        }

        //server.state = State.FINISHED;
        //System.out.println("State set to finished...");
        try
        {
            server.join();
        }
        catch (Exception exception)
        {
            System.out.println("Exception waiting for join: " + exception.getMessage());
        }
        //this part tests transmitting a file -------------------------------------------------------------
        server = new BurgessServer();
        server.start();

        try
        {
            testsocket = new DatagramSocket(DEFAULT_SERVER_OUT_PORT);//listen to the socket the server will send to
            teststr = DEFAULT_FILENAME;
            //System.out.println(teststr);
            int testpsize = 50;
            byte[] strdata = teststr.getBytes();
            byte[] intdata = intToBytes(testpsize);

            byte[] testdata = concatBytes(intdata, strdata);

            address = InetAddress.getLocalHost();

            DatagramPacket testpacket = new DatagramPacket(testdata, testdata.length, address, DEFAULT_SERVER_IN_PORT);
            testsocket.send(testpacket);

            //get what's sent back

            while (true)
            {
                byte[] listenbuff = new byte[testpsize];
                DatagramPacket lpacket = new DatagramPacket(listenbuff, listenbuff.length);
                testsocket.receive(lpacket);

                byte[] ldata = lpacket.getData();

                //this checks to see if the transmission is complete
                String check = new String(ldata).trim();
                if (check.equals(TRANSMISSION_COMPLETE_STRING))
                {
                    break;
                }

                int lnum = bytesToInt(subBytes(ldata, 0, 4));
                String ltext = new String(subBytes(ldata, 4, ldata.length));

                System.out.print("|" + lnum + "|" + ltext);
            }//end while stillWaiting
            System.out.println();

            //now send the fail
            byte[] faildata = FAIL_STRING.getBytes();
            DatagramPacket fail = new DatagramPacket(faildata, faildata.length, address, DEFAULT_SERVER_IN_PORT);
            testsocket.send(fail);

            //get what's sent back
            while (true)
            {
                byte[] listenbuff = new byte[testpsize];
                DatagramPacket lpacket = new DatagramPacket(listenbuff, listenbuff.length);
                testsocket.receive(lpacket);

                byte[] ldata = lpacket.getData();

                //this checks to see if the transmission is complete
                String check = new String(ldata).trim();
                if (check.equals(TRANSMISSION_COMPLETE_STRING))
                {
                    break;
                }

                int lnum = bytesToInt(subBytes(ldata, 0, 4));
                String ltext = new String(subBytes(ldata, 4, ldata.length));

                System.out.print("|" + lnum + "|" + ltext);
            }//end while stillWaiting
            System.out.println();

            //now send the ack
            byte[] ackdata = SUCCESS_STRING.getBytes();
            DatagramPacket ack = new DatagramPacket(ackdata, ackdata.length, address, DEFAULT_SERVER_IN_PORT);
            testsocket.send(ack);
        }
        catch (Exception exception)
        {
            System.out.println("Error in testing transmission: " + exception.getMessage());
        }

        try
        {
            server.join();
        }
        catch (Exception exception)
        {
            System.out.println("Exception waiting for join: " + exception.getMessage());
        }
    }//end method test
}//end class ProjectServer