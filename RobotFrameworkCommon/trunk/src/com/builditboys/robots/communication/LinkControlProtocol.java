package com.builditboys.robots.communication;

import com.builditboys.robots.infrastructure.AbstractNotification;
import static com.builditboys.robots.communication.LinkParameters.*;

public class LinkControlProtocol extends AbstractProtocol {

	public enum CommControlRoleEnum {
		MASTER, SLAVE;
	};

	private CommControlRoleEnum role;

	public static AbstractProtocol indicator = new LinkControlProtocol();

	// --------------------------------------------------------------------------------
	// Constructors

	private LinkControlProtocol() {
	}

	public LinkControlProtocol(CommControlRoleEnum rol) {
		role = rol;
	}

	//--------------------------------------------------------------------------------
	// Channel factories
	
	public InputChannel getInputChannel () {
		channel = new InputChannel(this, COMM_CONTROL_CHANNEL_NUMBER);
		return (InputChannel) channel;
	}
	
	public OutputChannel getOutputChannel () {
		channel = new OutputChannel(this, COMM_CONTROL_CHANNEL_NUMBER);
		return (OutputChannel) channel;
	}
	
	// --------------------------------------------------------------------------------

	public AbstractProtocol getIndicator() {
		return indicator;
	}

	// --------------------------------------------------------------------------------
	// Link Control Messages - keep in sync with the PSoC
	
	public static final int MS_DO_PREPARE = 0;
	public static final int MS_DO_PROCEED = 1;

	public static final int SM_NEED_DO_PREPARE = 2;
	public static final int SM_DID_PREPARE = 3;
	public static final int SM_DID_PROCEED = 4;

	public static final int IM_ALIVE = 5;

	/*
    xxx.ordinal() does not work in the switch statement the decodes the messages
    
	private enum LinkControlMessageEnum {
		MS_DO_PREPARE,
		MS_DO_PROCEED,
		
		SM_NEED_DO_PREPARE,
		SM_DID_PREPARE,
		SM_DID_PROCEED,
		
		IM_ALIVE;
	}
	*/

	// --------------------------------------------------------------------------------
	// Sending messages - Master to Slave messages

	public void sendDoPrepare(boolean doWait) throws InterruptedException {
		LinkMessage message = new LinkMessage(channelNumber, doWait);
		message.addByte((byte) MS_DO_PREPARE);
		channel.addMessage(message);
		if (doWait) {
			message.doWait();
		}
	}

	public void sendDoProceed(boolean doWait) throws InterruptedException {
		LinkMessage message = new LinkMessage(channelNumber, doWait);
		message.addByte((byte) MS_DO_PROCEED);
		channel.addMessage(message);
		if (doWait) {
			message.doWait();
		}
	}

	// --------------------------------------------------------------------------------
	// Sending messages - Slave to Master messages

	public void sendNeedDoPrepare(boolean doWait) throws InterruptedException {
		LinkMessage message = new LinkMessage(channelNumber, doWait);
		message.addByte((byte) SM_NEED_DO_PREPARE);
		channel.addMessage(message);
		if (doWait) {
			message.doWait();
		}
	}

	public void sendDidPrepare(boolean doWait) throws InterruptedException {
		LinkMessage message = new LinkMessage(channelNumber, doWait);
		message.addByte((byte) SM_DID_PREPARE);
		channel.addMessage(message);
		if (doWait) {
			message.doWait();
		}
	}

	public void sendDidProceed(boolean doWait) throws InterruptedException {
		LinkMessage message = new LinkMessage(channelNumber, doWait);
		message.addByte((byte) SM_DID_PROCEED);
		channel.addMessage(message);
		if (doWait) {
			message.doWait();
		}
	}

	// --------------------------------------------------------------------------------
	// Sending messages - Shared messages

	public void sendKeepAlive() {
		LinkMessage message = new LinkMessage(channelNumber);
		message.addByte((byte) IM_ALIVE);
		channel.addMessage(message);
	}

	// --------------------------------------------------------------------------------
	// Receiving messages
	
	public void receiveMessage(LinkMessage message) {
		AbstractLink link = channel.getLink();
		int indicator = message.getByte(0);
		switch (indicator) {
		case MS_DO_PREPARE:
			link.receivedDoPrepare(channel, message);
			break;
		case MS_DO_PROCEED:
			link.receivedDoProceed(channel, message);
			break;

		case SM_NEED_DO_PREPARE:
			link.receivedNeedDoPrepare(channel, message);
			break;
		case SM_DID_PREPARE:
			link.receivedDidPrepare(channel, message);
			break;
		case SM_DID_PROCEED:
			link.receivedDidProceed(channel, message);
			break;

		case IM_ALIVE:
			link.receivedImAlive(channel, message);
			break;

		default:
			throw new IllegalStateException();
		}
	}

	// --------------------------------------------------------------------------------

	public AbstractNotification deSerialize(LinkMessage message) {
		return null;
	}

	public LinkMessage serialize(AbstractNotification notice) {
		return null;
	}

}
