import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class sk {
    //process interface 
    public interface Process extends Remote {
        void requestCriticalSection() throws RemoteException;
        void releaseCriticalSection() throws RemoteException;
        int getSequenceNumber() throws RemoteException;
        void grantCriticalSection() throws RemoteException;
    }

    //process implimentation
    public static class ProcessImpl extends UnicastRemoteObject implements Process {
        private final int processId;
        private final TokenManager tokenManager;
        private final AtomicInteger sequenceNumber;
        private boolean inCriticalSection;

	//setter method for each process
        protected ProcessImpl(int processId, TokenManager tokenManager) throws RemoteException {
	    //setting PID
            this.processId = processId;
	    //setting token manager
            this.tokenManager = tokenManager;
	    //setting seq. #
            this.sequenceNumber = new AtomicInteger(0);
	    //in CS bool flag
            this.inCriticalSection = false;
        }

        //functioni to request CS
        public synchronized void requestCriticalSection() throws RemoteException {
	    //increment seq # and printing for logic to be seen
            int currentSeqNum = sequenceNumber.incrementAndGet();
            System.out.println("Process " + processId + " requesting critical section with sequence number: " + currentSeqNum);
	    //notifying token manager of CS request
            tokenManager.requestEntry(processId, currentSeqNum);
        }

        //function to release the CS and notify TM
        public synchronized void releaseCriticalSection() throws RemoteException {
            System.out.println("Process " + processId + " releasing critical section.");
	    //reseting boolean flag
            inCriticalSection = false;
	    //releasing token and processing next request
            tokenManager.releaseToken(processId, sequenceNumber.get());
        }

        //function to get seq # of process that called
        public int getSequenceNumber() throws RemoteException {
            return sequenceNumber.get();
        }

        //function to grant CS
        public synchronized void grantCriticalSection() throws RemoteException {
            System.out.println("Process " + processId + " granted critical section.");
	    //resetting in CS flag
            inCriticalSection = true;
        }
    }

    //TM interface
    public interface TokenManager extends Remote {
        void requestEntry(int processId, int sequenceNumber) throws RemoteException;
        void releaseToken(int processId, int sequenceNumber) throws RemoteException;
    }

    //TM implimentation
    public static class TokenManagerImpl extends UnicastRemoteObject implements TokenManager {
	//creating current token holder variable
        private Integer currentTokenHolder;
	//creating the request Q
        private final PriorityQueue<Request> requestQueue;

	//setter method for token manager
        protected TokenManagerImpl() throws RemoteException {
            //initializing current token holder
	    this.currentTokenHolder = null;
	    //sorting by sequence numver and defining tie breaker as PID
            this.requestQueue = new PriorityQueue<>(Comparator.comparingInt((Request r) -> r.sequenceNumber)
                    .thenComparingInt(r -> r.processId));
        }

        //function to request an entry from the TM
        public synchronized void requestEntry(int processId, int sequenceNumber) throws RemoteException {
            System.out.println("TokenManager received request from Process " + processId + " with sequence number: " + sequenceNumber);
	    //adding request to the Q
            requestQueue.add(new Request(processId, sequenceNumber));
	    //granting access to CS if the next process is elgible
            grantNextIfEligible();
        }

        //function to release the token
        public synchronized void releaseToken(int processId, int sequenceNumber) throws RemoteException {
            System.out.println("TokenManager releasing token from Process " + processId);
	    //if the Q is not empty grant next process access to CS if elgible
            if (!requestQueue.isEmpty()) {
                grantNextIfEligible();
            }
        }

	//granting next process CS access if elgible
        private synchronized void grantNextIfEligible() throws RemoteException {
	    //if there is no token holder and Q is not empty
            if (currentTokenHolder == null && !requestQueue.isEmpty()) {
		//getting highest priority process from Q
                Request nextRequest = requestQueue.poll();
		//settign current toekn holder
                currentTokenHolder = nextRequest.processId;
                System.out.println("TokenManager granting token to Process " + currentTokenHolder);
                try {
		    //looking up process in RMI
                    Process process = (Process) Naming.lookup("rmi://localhost/Process" + currentTokenHolder);
		    //grant that process the CS
                    process.grantCriticalSection();
		    //catching errors
                } catch (NotBoundException e) {
                    System.err.println("Error: Process " + currentTokenHolder + " is not bound in the registry.");
                } catch (MalformedURLException e) {
                    System.err.println("Error: Malformed URL for Process " + currentTokenHolder);
                }
            }
        }

	//class for each request
        private static class Request {
	    //includes PID and seq #
            int processId;
            int sequenceNumber;

	    //setter class for each req
            Request(int processId, int sequenceNumber) {
                this.processId = processId;
                this.sequenceNumber = sequenceNumber;
            }
        }
    }

    //main
    public static void main(String[] args) {
        try {

	    //testing for server
            if (args[0].equalsIgnoreCase("server")) {
                //starting the RMI registry from code instead of terminal
                try {
                    LocateRegistry.createRegistry(1099);
                    System.out.println("RMI registry started on port 1099.");
                } catch (RemoteException e) {
                    System.out.println("RMI registry already running.");
                }

                //starting the server and binding the TM
                TokenManager tokenManager = new TokenManagerImpl();
                Naming.rebind("rmi://localhost/TokenManager", tokenManager);
                System.out.println("TokenManager bound in RMI registry.");


	      //testing for client
            } else if (args[0].equalsIgnoreCase("client")) {
                //starting the client process
		//getting the PID from user
                int processId = Integer.parseInt(args[1]);
		//lookking up the TM
                TokenManager tokenManager = (TokenManager) Naming.lookup("rmi://localhost/TokenManager");
		//creating and binding the process to designated directory
                Process process = new ProcessImpl(processId, tokenManager);
                Naming.rebind("rmi://localhost/Process" + processId, process);

                //simulating the processes logic
                process.requestCriticalSection();
                //simulating a process in the CS performing a task
		Thread.sleep(2000); 
		//process releasing the CS 
                process.releaseCriticalSection();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

