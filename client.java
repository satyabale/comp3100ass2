import java.net.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.w3c.dom.*;

import java.io.*;

public class Client {
    // initialize socket and input output streams
    private static Socket socket = null;
    private BufferedReader input = null;
    private DataOutputStream out = null;
    private BufferedReader in = null;

    // constructor to put ip address and port
    public Client(String address, int port) throws IOException {
        connect(address, port);
        input = new BufferedReader(new InputStreamReader(System.in));
        out = new DataOutputStream(socket.getOutputStream());
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    private void start() {
        sendMessage("HELO");
        readMessage();

        // Send user details
        sendMessage("AUTH " + System.getProperty("user.name"));
        readMessage();

        // handshake completed
        boolean connected = true;

        // populate arrayList of server objects from:
        ArrayList<Server> servers = new ArrayList<Server>();

        // arrayList for holding job info from "GETS Capable"
        ArrayList<Job> jobs = new ArrayList<Job>();

        // Tells client it is ready to recieve commands
        sendMessage("REDY");

        // temp string to hold readMessage data
        // we check the contents of this string, rather than call readMessage()
        String msg = readMessage();

        // SCHEDULES JOB TO LARGEST SERVER
        while (connected) {
            // Job completed we tell ds-server we are ready
            if (msg.contains("JCPL")) {
                sendMessage("REDY");
                msg = readMessage();
                // there are no more jobs left
            } else if (msg.contains("NONE")) { // there are no more jobs left
                connected = false;
                sendMessage("QUIT");
            } else {

                // Get next message
                if (msg.contains("OK")) {
                    sendMessage("REDY");
                    msg = readMessage();
                }

                // we have a JOB incoming, so we create a job objet based on it
                if (msg.contains("JOBN")) {
                    jobs.add(parseJob(msg)); // create job

                    // the job arrayList will only ever have 1 item in it at a time...
                    sendMessage(getCapable(jobs.get(0))); // GETS Capable called
                    msg = readMessage();

                    sendMessage("OK");

                    // list of capable servers are added to arrayList of server objects
                    msg = readMessage();
                    servers = parseServer(msg);
                    sendMessage("OK");

                    // we should receive a "." here
                    msg = readMessage();

                    sendMessage(customAlgorithm (servers, jobs)); // Scheduling algorithm called here
                    msg = readMessage();

                    // only need one job at a time
                    jobs.remove(0);
                }
            }
        }

        // close the connection
        try {

            // QUIT hand-shake, must receive confirmation from server for quit
            if (readMessage().contains("QUIT")) {
                input.close();
                out.close();
                socket.close();
            }

        } catch (IOException i) {
            // System.out.println(i);
        }

        // Exit the program
        System.exit(1);
    }

    // Sends all jobs to largest server
    // private String AllToLargest(String job, Server s){
    // String[] strSplit = job.split("\\s+");
    // return "SCHD " + strSplit[2] + "" + s.getType() + "" + (s.getLimit()-1);
    // }

    // Finds the largest server; counts through cores until largest is found then
    // returns largest
    // private int findLargestServer(ArrayList<Server> s) {
    //     int largest = 0;
    //     for (int i = 0; i < s.size(); i++) {
    //         if (s.get(i).getCores() > largest) {
    //             largest = i;
    //         }
    //     }
    //     return largest;
    // }

    // Send message to server
    private void sendMessage(String outStr) {
        byte[] byteMsg = outStr.getBytes();
        try {
            out.write(byteMsg);
        } catch (IOException e) {
         //   e.printStackTrace();
        }

        // Display output from client
    //    System.out.println("OUT: " + outStr);
    }

    // Read message from server
    private String readMessage() {
        String inStr = "";
        char[] cbuf = new char[Short.MAX_VALUE * 2];
        try {
            in.read(cbuf);
        } catch (IOException e) {
        //    e.printStackTrace();
        }
        inStr = new String(cbuf, 0, cbuf.length);

        // Display input from server
    //    System.out.println("INC: " + inStr);

        return inStr;
    }

    public ArrayList<Server> parseServer(String s) {

        // remove trailing spaces
        s = s.trim();

        // temp arrayList to be passed back
        ArrayList<Server> List = new ArrayList<Server>();

        // split strings by newline
        String[] lines = s.split("\\r?\\n");

        for (String line : lines) {

            // split each line by white space
            String[] splitStr = line.split("\\s+");

            // server type server ID state curStart Time core count memory disk wJobs rJobs
            Server server = new Server(splitStr[0], Integer.parseInt(splitStr[1]), splitStr[2],
                    Integer.parseInt(splitStr[3]), Integer.parseInt(splitStr[4]), Integer.parseInt(splitStr[5]),
                    Integer.parseInt(splitStr[6]), Integer.parseInt(splitStr[7]), Integer.parseInt(splitStr[8]));
            List.add(server);
        }

        return List;
    }

    //
    // create a new job object
    //
    public Job parseJob(String job) {

        // remove trailing spaces
        job = job.trim();

        // split string up by white space; "\\s+" is a regex expression
        String[] splitStr = job.split("\\s+");

        Job j = new Job(Integer.parseInt(splitStr[1]), Integer.parseInt(splitStr[2]), Integer.parseInt(splitStr[3]),
                Integer.parseInt(splitStr[4]), Integer.parseInt(splitStr[5]), Integer.parseInt(splitStr[6]));

        // returns job object to fill arrayList
        return j;
    }

    public String getCapable(Job j) {

        // grab info from job inputted job object
        return ("GETS Capable " + j.getCoreReq() + " " + j.getMemoryReq() + " " + j.getDiskReq());
    }

    public String customAlgorithm (ArrayList<Server> servers, ArrayList<Job> job){

		// Server information string
		String ServerInfo = "";

		for (Server server: servers) {
            int coreReq =  server.getCores() - job.get(0).getCoreReq();
			// find best fit for job
			if (// server.getDisk() >= job.get(0).getDiskReq() &&
				server.getCores() >= job.get(0).getCoreReq() &&
			//	server.getMemory() >= job.get(0).getMemoryReq()){ 
				 job.get(0).getStartTime() >= job.get(0).getRunTime()) {
					// Ensure the server is already active or idle to reduce cost
					ServerInfo = server.getType() + " " + server.getID();
					return "SCHD " + job.get(0).getID() + " " + ServerInfo;
			}
			// When there is no optimal server, just use first server.
			else {
				// Send job to first server
				ServerInfo = servers.get(0).getType() + " " + servers.get(0).getID();
			}
		}
		// There is only one job in queue so schedule it
		return "SCHD " + job.get(0).getID() + " " + ServerInfo;
	}


    // Establishes connection to initiate handhsake
    private static void connect(String address, int port) {
        double secondsToWait = 1;
        int tryNum = 1;
        while (true) {
            try {
            //    System.out.println("Connecting to server at: " + address + ":" + port);
                socket = new Socket(address, port);
            //    System.out.println("Connected");
                break;
            } catch (IOException e) {
                secondsToWait = Math.min(30, Math.pow(2, tryNum));
                tryNum++;
            //    System.out.println("Connection timed out, retrying in  " + (int) secondsToWait + " seconds ...");
                try {
                    TimeUnit.SECONDS.sleep((long) secondsToWait);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }
    }

    public static void main(String args[]) throws IOException {
        Client client = new Client("127.0.0.1", 50000);
        client.start();
    }

}