package bobby;

import java.net.*;
import java.io.*;
import java.util.*;

import java.util.concurrent.Semaphore;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ScotlandYard implements Runnable{

	/*
		this is a wrapper class for the game.
		It just loops, and runs game after game 
		look at the run() function to code
	*/

	public int port;
	public int gamenumber;

	public ScotlandYard(int port){
		this.port = port;
		this.gamenumber = 0;
	}

	public void run(){
		while (true){
			Thread tau = new Thread(new ScotlandYardGame(this.port, this.gamenumber));
			tau.start();
			try{
				tau.join();
			}
			catch (InterruptedException e){
				return;
			}
			this.gamenumber++;
		}
	}

	public class ScotlandYardGame implements Runnable{
		private Board board;
		private ServerSocket server;
		public int port;
		public int gamenumber;
		private ExecutorService threadPool;

		public ScotlandYardGame(int port, int gamenumber){
			this.port = port;
			this.board = new Board();
			this.gamenumber = gamenumber;
			try{
				this.server = new ServerSocket(port);
				System.out.println(String.format("Game %d:%d on", port, gamenumber));
				server.setSoTimeout(5000);
			}
			catch (IOException i) {
				return;
			}
			this.threadPool = Executors.newFixedThreadPool(10);
		}


		public void run(){

			try{
			
				//INITIALISATION: get the game going

				

				Socket socket = null;
				boolean fugitiveIn = false;
				
				/*
				listen for a client to play fugitive, and spawn the moderator.
				
				here, it is actually ok to edit this.board.dead, because the game hasn't begun
				*/
				
				do{
			        try 
					{
						socket = server.accept();
					}
					catch (SocketTimeoutException e)
					{
						continue;
					}
					fugitiveIn = true;
					//.Do we have to increase the number of totalThreads here???
					board.threadInfoProtector.acquire();
					this.board.dead = false;
					board.totalThreads += 1;
					board.threadInfoProtector.release();
				} while (!fugitiveIn);
				
				System.out.println(this.gamenumber);

				// Spawn a thread to run the Fugitive
                                             
				Thread fugitiveThread = new Thread(new ServerThread(board, -1, socket, port, gamenumber));
				threadPool.submit(fugitiveThread); //.Take this with a pinch of salt, not sure if it's the right command

				// Spawn the moderator
                Thread moderatorThread = new Thread(new Moderator(board));
				threadPool.submit(moderatorThread);
                
				while (true){ //.this while loop exit takes place by this.board.dead == true.
					/*
					listen on the server, accept connections
					if there is a timeout, check that the game is still going on, and then listen again!
					*/

					try {
						socket = server.accept(); //.doing this with a pinch of salt not sure if I can use the variable socket
						//but  most probably seems right
					} 
					catch (SocketTimeoutException t){
                        
						Boolean decision; //.Not confident about this idea but seems right
						board.threadInfoProtector.acquire();
                        decision = board.dead; 
						board.threadInfoProtector.release();
       
                        if(decision) break;
						else continue;                    
					}
					
					
					/*
					acquire thread info lock, and decide whether you can serve the connection at this moment,

					if you can't, drop connection (game full, game dead), continue, or break.

					if you can, spawn a thread, assign an ID, increment the totalThreads

					don't forget to release lock when done!
					*/
					                                         
                          
                    board.threadInfoProtector.acquire();
					if(board.dead == true)
					{	//.game dead
						socket.close();
						board.threadInfoProtector.release();
						break;
					}
					int assignId = board.getAvailableID();
					if(assignId == -1)
					{	//.game full
						socket.close();
						board.threadInfoProtector.release();
						continue;
					}
					board.threadInfoProtector.release();
                    Thread detectiveThread = new Thread(new ServerThread(board, assignId, socket, port, gamenumber));
					board.threadInfoProtector.acquire();
					board.totalThreads += 1;
					board.threadInfoProtector.release();
					threadPool.submit(detectiveThread);
				}

				/* Note that we are out of the while loop now
				reap the moderator thread, close the server,
				
				kill threadPool (Careless Whispers BGM stops)
				*/
                moderatorThread.join();
				server.close();
				threadPool.shutdown(); //.shutdownNow()?              
    
				System.out.println(String.format("Game %d:%d Over", this.port, this.gamenumber));
				return;
			}
			catch (InterruptedException ex){
				System.err.println("An InterruptedException was caught: " + ex.getMessage());
				ex.printStackTrace();
				return;
			}
			catch (IOException i){
				return;
			}
			
		}

		
	}

	public static void main(String[] args) {
		for (int i=0; i<args.length; i++){
			int port = Integer.parseInt(args[i]);
			Thread tau = new Thread(new ScotlandYard(port));
			tau.start();
		}
	}
}