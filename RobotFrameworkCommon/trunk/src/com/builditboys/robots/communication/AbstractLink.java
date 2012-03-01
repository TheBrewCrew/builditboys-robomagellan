package com.builditboys.robots.communication;

import static com.builditboys.robots.communication.LinkParameters.*;

import com.builditboys.robots.time.*;

// a comm link hold together all the pieces
// the port
// the send and receive threads
// the input and output channel collections

public abstract class AbstractLink implements Runnable {

	protected Sender sender;
	protected Receiver receiver;

	protected LinkPortInterface commPort;

	protected InputChannelCollection inputChannels;
	protected OutputChannelCollection outputChannels;

	protected volatile ThreadControlEnum threadControl;
	protected String threadName;
	protected Thread thread;
	protected volatile boolean shouldRun;
	protected volatile boolean suspended;
	
	protected InputChannel controlChannelIn;
	protected OutputChannel controlChannelOut;

	protected LinkControlProtocol iprotocol;
	protected LinkControlProtocol oprotocol;
	
	protected long lastKeepAliveSentTime = 0;
	protected long lastKeepAliveReceivedTime = 0;
		
	// --------------------------------------------------------------------------------
	// Constructors

	protected AbstractLink(LinkPortInterface port) {
		commPort = port;
		inputChannels = new InputChannelCollection(this);
		outputChannels = new OutputChannelCollection(this);
		sender = new Sender(this, commPort);
		receiver = new Receiver(this, commPort);
	}

	// --------------------------------------------------------------------------------
	// Starting things up
	
	// see startThreads below
	
	// enable and disable let you turn on and off normal message processing
	public abstract void enable();
	public abstract void disable();
	
	// --------------------------------------------------------------------------------
	// Channel collections
	
	protected InputChannelCollection getInputChannels() {
		return inputChannels;
	}

	protected OutputChannelCollection getOutputChannels() {
		return outputChannels;
	}

	// --------------------------------------------------------------------------------
	// Finding channels
	
	public AbstractChannel getInputChannelN (int channelNumber) {
		return inputChannels.getChannelByNumber(channelNumber);
	}
	
	public AbstractChannel getOutputChannelN (int channelNumber) {
		return outputChannels.getChannelByNumber(channelNumber);
	}
	
	public AbstractChannel getInputChannelByProtocol (AbstractProtocol protocol) {
		return inputChannels.getChannelByProtocol(protocol);
	}
	
	public AbstractChannel getOutputChannelByProtocol (AbstractProtocol protocol) {
		return outputChannels.getChannelByProtocol(protocol);
	}
	
	// --------------------------------------------------------------------------------
	// Adding a protocol
	
	public void addProtocol (AbstractProtocol iproto, AbstractProtocol oproto) {
		InputChannel channelIn = iproto.getInputChannel();
		OutputChannel channelOut = oproto.getOutputChannel();

		AbstractChannel.pairChannels(channelIn, channelOut);

		inputChannels.addChannel(channelIn);
		outputChannels.addChannel(channelOut);
	}

	// --------------------------------------------------------------------------------
	// Run

	public void run() {
		while (true) {
			try {
				checkThreadControl();
			} catch (InterruptedException e) {
					System.out.println(threadName + " check interrupted");
					continue;
			}
			// check said to exit
			// check resumed from a wait
			// check said to keep running
			// if the check was interrupted, control went back to the top
			if (!shouldRun) {
				break;
			}
			try {
				doWork();
			} catch (InterruptedException e) {
				System.out.println(threadName + " work interrupted");
			}
		}
	}

	public abstract void doWork() throws InterruptedException;

	// --------------------------------------------------------------------------------
	// Thread control for all of the link's threads
	
	public void startThreads(String threadNm) {
		startThread(threadNm + " Master Comm Link");
		sender.startThread(threadName + " Sender");
		receiver.startThread(threadName + " Receiver");
	}

	public void stopThreads() {
		stopThread();
		sender.stopThread();
		receiver.stopThread();
	}

	public void joinThreads() throws InterruptedException {
		sender.threadJoin();
		receiver.threadJoin();
		threadJoin();
	}

	// --------------------------------------------------------------------------------

	public void threadJoin() throws InterruptedException {
		thread.join();
	}

	// --------------------------------------------------------------------------------
	// Thread control for the main link thread

	public String getThreadName() {
		return threadName;
	}

	public void startThread(String threadName) {
		if (thread != null) {
			throw new IllegalStateException();
		}
		this.threadName = threadName;
		shouldRun = true;
		threadControl = ThreadControlEnum.RUN;
		thread = new Thread(this, threadName);
		System.out.println("Starting " + threadName);
		thread.start();
	}

	public void suspendThread() {
		threadControl = ThreadControlEnum.SUSPEND;
		thread.interrupt();
	}

	public void resumeThread() {
		if (suspended) {
			threadControl = ThreadControlEnum.RUN;
			notify();
		}
		else {
			throw new IllegalStateException();
		}
	}

	public void stopThread() {
		threadControl = ThreadControlEnum.STOP;
		thread.interrupt();
	}

	protected synchronized void checkThreadControl() throws InterruptedException {
		do {
			switch (threadControl) {
			case SUSPEND:
				suspended = true;
				wait();
				suspended = false;
				break;
			case RUN:
				break;
			case STOP:
				shouldRun = false;
				break;
			default:
				throw new IllegalStateException();
			}
		// loop to avoid spurious awakenings
		} while (threadControl == ThreadControlEnum.SUSPEND);
	}

	// --------------------------------------------------------------------------------

	protected boolean keepAliveOk () {
		return ((Clock.clockRead() - lastKeepAliveReceivedTime) < IM_ALIVE_TIMEOUT);
	}
	
	protected long timeToNextKeepAlive () {
		return ((lastKeepAliveSentTime + KEEP_ALIVE_INTERVAL) - Clock.clockRead());
	}
	
	// --------------------------------------------------------------------------------
	// These methods are used for the receive side to communicate to the
	// link important information about how the link is working
	
	// slave to master
	protected void receivedNeedDoPrepare (AbstractChannel rchannel, LinkMessage message) {
		throw new IllegalStateException();
	}
	
	protected void receivedDidPrepare (AbstractChannel rchannel, LinkMessage message) {
		throw new IllegalStateException();
	}

	protected void receivedDidProceed (AbstractChannel rchannel, LinkMessage message) {
		throw new IllegalStateException();
	}
	
	
	// master to slave
	protected void receivedDoPrepare (AbstractChannel rchannel, LinkMessage message) {
		throw new IllegalStateException();
	}

	protected void receivedDoProceed (AbstractChannel rchannel, LinkMessage message) {
		throw new IllegalStateException();
	}
	
	
	// both directions
	protected void receivedImAlive (AbstractChannel rchannel, LinkMessage message) {
		throw new IllegalStateException();
	}
	
	
	// if receive problems
	protected abstract void receiveReceiverException (Exception e);
	
	// --------------------------------------------------------------------------------

	public abstract boolean isSendableChannel (AbstractChannel channel);
	public abstract boolean isReceivableChannel (AbstractChannel channel);
	public abstract boolean isForceInitialSequenceNumbers ();
	
	protected void linkWait (long timeout) throws InterruptedException {
//		System.out.println(getRole() + " start wait");
		wait(timeout);
//		System.out.println(getRole() + " end wait");
	}
	
	// --------------------------------------------------------------------------------
	
	public abstract String getRole ();

}
