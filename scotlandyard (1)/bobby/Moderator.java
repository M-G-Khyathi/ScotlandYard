package bobby;

import java.net.*;
import java.io.*;
import java.util.*;

import java.util.concurrent.Semaphore;

public class Moderator implements Runnable{
	private Board board;

	public Moderator(Board board){
		this.board = board;
	}

	public void run(){
		while (true){
			try{
				/*acquire permits: 
				
				1) the moderator itself needs a permit to run, see Board
				2) one needs a permit to modify thread info

				*/
                                          
                board.moderatorEnabler.acquire();                        
				board.threadInfoProtector.acquire();

				/* 
				look at the thread info, and decide how many threads can be 
				permitted to play next round

				playingThreads: how many began last round
				quitThreads: how many quit in the last round
				totalThreads: how many are ready to play next round

				RECALL the invariant mentioned in Board.java

				T = P - Q + N

				P - Q is guaranteed to be non-negative.
				*/

				//base case
				
				if (this.board.embryo){ //.Not sure If I wrote the base case properly
					                              
                    board.threadInfoProtector.release();               
                                              
					continue;
				}
				
				
				//find out how many newbies
				int newbies = board.totalThreads - board.playingThreads + board.quitThreads;


				/*
				If there are no threads at all, it means Game Over, and there are no 
				more new threads to "reap". dead has been set to true, then 
				the server won't spawn any more threads when it gets the lock.

				Thus, the moderator's job will be done, and this thread can terminate.
				As good practice, we will release the "lock" we held. 
				*/

				if(board.totalThreads==0)
				{
					this.board.dead = true;
					board.threadInfoProtector.release();
					//. board.moderatorEnabler.release(); //.Not sure, skeptic yet
					break;
				}
     
				
				/* 
				If we have come so far, the game is afoot.

				totalThreads is accurate. 
				Correct playingThreads
				reset quitThreads


				Release permits for threads to play both re entry and registration, and the permit to modify thread info
				*/

				board.playingThreads = board.totalThreads;
				board.quitThreads = 0;

				//.Threads playing their first round (newbies) need registration. All threads playing the round need reentry.
				if(newbies>0) board.registration.release(newbies);
				board.reentry.release(board.playingThreads);
				//.Note sure if release() is the right function
				//.But what happens if they release the 5 tickets in this round and if the threads accept and close then in the next round there would be a problem
				//.Because release() does not the set the number of permits it increments it by that amount
				board.threadInfoProtector.release();
                                             
			}
			catch (InterruptedException ex){
				System.err.println("An InterruptedException was caught: " + ex.getMessage());
				ex.printStackTrace();
				return;
			}
		}
	}
}