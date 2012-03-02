package com.builditboys.robots.communication;

import static com.builditboys.robots.communication.LinkParameters.*;

import com.builditboys.robots.time.Time;

public class SlaveLink extends AbstractLink implements Runnable {
	
	private enum LinkStateEnum {	
		LinkInitState,
		
		LinkSentNeedDoPrepareState,
		
		LinkReceivedDoPrepareState,
		LinkSentDidPrepareState,
		
		LinkReceivedDoProceedState,
		LinkSentDidProceedState,
		
		LinkReceivedImAliveState,
		
		LinkReadyState,
		
		LinkActiveState;
	}

	protected LinkStateEnum linkState;
	
	//--------------------------------------------------------------------------------
	// Constructors

	public SlaveLink (LinkPortInterface port) {
		super(port);
		setLinkState(LinkStateEnum.LinkInitState, "constructor");
		
		// the protocol's channel will be set when the protocol is associated with a channel
		iprotocol = new LinkControlProtocol(LinkControlProtocol.CommControlRoleEnum.SLAVE);
		oprotocol = new LinkControlProtocol(LinkControlProtocol.CommControlRoleEnum.SLAVE);
	
		controlChannelIn = iprotocol.getInputChannel();
		controlChannelOut = oprotocol.getOutputChannel();

		AbstractChannel.pairChannels(controlChannelIn, controlChannelOut);

		inputChannels.addChannel(controlChannelIn);
		outputChannels.addChannel(controlChannelOut);
	}

	// --------------------------------------------------------------------------------
	
	public void enable() {
		if (linkState == LinkStateEnum.LinkReadyState) {
			linkState = LinkStateEnum.LinkActiveState;
		}
		else {
			throw new IllegalStateException();
		}
	}
	
	public void disable() {
		if (linkState == LinkStateEnum.LinkActiveState) {
			linkState = LinkStateEnum.LinkReadyState;
		}
		// for any other states, just do nothing since you are already
		// effectively disabled
	}

	// --------------------------------------------------------------------------------
	// Do some work, the top level, gets called in a loop

	// be sure to look at isSendableChannel, etc to understand how the various
	// states affect the actions of the sender and receiver

	public synchronized void doWork () throws InterruptedException {
		while (true) {
			setLinkState(LinkStateEnum.LinkInitState, "one");
			
			// --------------------
			// wait a little to give the master a chance to start things
			// off before the slave starts chiming in
			linkWait(SLAVE_START_DELAY);

			// --------------------
			// if the master has not already started the init process
			// remind the master to do so by sending a NEED_DO_PREPARE
			// if the master did start things already, just fall through
			if (linkState != LinkStateEnum.LinkReceivedDoPrepareState) {
				oprotocol.sendNeedDoPrepare(false);
				setLinkState(LinkStateEnum.LinkSentNeedDoPrepareState, "two");
				linkWait(NEED_PREPARE_TIMEOUT);
			}

			// --------------------
			// if we got a DO_PREPARE, then send DID_PREPARE
			if (linkState == LinkStateEnum.LinkReceivedDoPrepareState) {
				// master told us to reset, so we do
				oprotocol.sendDidPrepare(false);
				setLinkState(LinkStateEnum.LinkSentDidPrepareState, "three");
				linkWait(DO_PROCEED_TIMEOUT);
			}
			else {
				// failure, start over
				continue;
			}

			// --------------------
			// if we got a DO_PROCEED, then send a DID_PROCEED
			if (linkState == LinkStateEnum.LinkReceivedDoProceedState) {
				oprotocol.sendDidProceed(false);
				setLinkState(LinkStateEnum.LinkSentDidProceedState, "four");
				linkWait(IM_ALIVE_TIMEOUT);
			}
			else {
				// failure, start over
				continue;
			}

			// --------------------
			// if we got an IM_ALIVE, then the link is happy
			// just keep it that way
			if (linkState == LinkStateEnum.LinkReceivedImAliveState) {
				setLinkState(LinkStateEnum.LinkReadyState, "five");
				lastKeepAliveReceivedTime = Time.getAbsoluteTime();
				while ((linkState == LinkStateEnum.LinkReadyState)
						|| (linkState == LinkStateEnum.LinkActiveState)) {
					// make sure you have recently received a keep alive message
					// also, you could be awakened by receiving a keep alive so 
					// keep track of how long you need to wait to send a keep alive
					if (keepAliveOk()) {
						long timeToNextSend = timeToNextKeepAlive();
						if (timeToNextSend <= 0) {
							oprotocol.sendKeepAlive();
							lastKeepAliveSentTime = Time.getAbsoluteTime();
						}
						else {
							linkWait(timeToNextSend);
						}
					}
					else {
						System.out.println("Slave Keep Alive Timout");
						break;
					}
				}
				// problems, time out or receive error, start over
				continue;
			}
			else {
				// failure, start over
				continue;
			}
		}
	}


	// --------------------------------------------------------------------------------
	// The receiver link control protocol calls this when it gets a link control message
	
	protected synchronized void receivedDoPrepare (AbstractChannel channel, LinkMessage message) {
		switch (linkState) {
		// need prepare state discards everything but a do prepare
		// its satisfied here
		case LinkInitState:
		case LinkSentNeedDoPrepareState:
			setLinkState(LinkStateEnum.LinkReceivedDoPrepareState, "six");
			notify();
			break;
		// otherwise, out of sync
		default:
			setLinkState(LinkStateEnum.LinkInitState, "seven");
			notify();
			break;
		}		
	}
	
	protected synchronized void receivedDoProceed (AbstractChannel channel, LinkMessage message) {
		switch (linkState) {
		// need prepare state discards everything but a do prepare
		case LinkInitState:
		case LinkSentNeedDoPrepareState:
			break;
		case LinkSentDidPrepareState:
			setLinkState(LinkStateEnum.LinkReceivedDoProceedState, "eight");
			notify();
			break;
		// otherwise, out of sync
		default:
			setLinkState(LinkStateEnum.LinkInitState, "nine");
			notify();
			break;
		}		
	}
		
	
	protected synchronized void receivedImAlive (AbstractChannel channel, LinkMessage message) {
		switch (linkState) {
		// need prepare state discards everything but a do prepare
		case LinkSentNeedDoPrepareState:
			break;
		// honor the request, go active
		case LinkSentDidProceedState:
			setLinkState(LinkStateEnum.LinkReceivedImAliveState, "ten");
			notify();
			break;
		// stay active
		case LinkReadyState:
		case LinkActiveState:
			break;
		// otherwise, out of sync
		default:
			setLinkState(LinkStateEnum.LinkInitState, "eleven");
			notify();
			break;
		}
		lastKeepAliveReceivedTime = Time.getAbsoluteTime();
	}
		
	// --------------------------------------------------------------------------------
	// The receiver calls this when it detects an error
	
	protected synchronized void receiveReceiverException (Exception e) {
		System.out.println("Slave Link Receive Exception");
		setLinkState(LinkStateEnum.LinkInitState, "twelve");
		notify();
	}
	
	// --------------------------------------------------------------------------------
	// Interaction with the sender and receiver
	
	public synchronized boolean isSendableChannel (AbstractChannel channel) {
//		synchronized (System.out){
//			System.out.println("slave is sendable");
//			System.out.println(controlChannelOut);
//			System.out.println(controlChannelIn);
//			System.out.println(channel);
//		}
		return (channel == controlChannelOut) || (linkState == LinkStateEnum.LinkActiveState);
	}
	
	public synchronized boolean isReceivableChannel (AbstractChannel channel) {
//		synchronized (System.out){
//			System.out.println("slave is receivable");
//			System.out.println(controlChannelOut);
//			System.out.println(controlChannelIn);
//			System.out.println(channel);
//		}
		return (channel == controlChannelIn) || (linkState == LinkStateEnum.LinkActiveState);
	}
	
	public synchronized boolean isForceInitialSequenceNumbers () {
		switch (linkState) {
		case LinkInitState:
		case LinkSentNeedDoPrepareState:
			return true;
		default:
			return false;
		}
	}


	// --------------------------------------------------------------------------------

	public String getRole () {
		return "Slave ";
	}
	
	// --------------------------------------------------------------------------------

	private void setLinkState (LinkStateEnum state, String where) {
//		System.out.println("Slave  -> " + state.toString() + " in " + where);
		linkState = state;
	}

}
