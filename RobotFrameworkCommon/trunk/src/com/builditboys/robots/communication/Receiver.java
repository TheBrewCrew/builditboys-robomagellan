package com.builditboys.robots.communication;

import static com.builditboys.robots.communication.LinkParameters.*;

import com.builditboys.robots.time.InternalTimeSystem;
import com.builditboys.robots.time.LocalTimeSystem;
import com.builditboys.robots.utilities.FillableBuffer;

public class Receiver extends AbstractSenderReceiver {

	private InputChannelCollection inputChannels;

	private int receivedSequenceNumber;
	private int receivedChannelNumber;
	private int receivedLength;
	private int receivedCRC1;
	private int receivedCRC2;
	private LinkMessage receivedMessage;
	private AbstractChannel receivedChannel;
	private AbstractProtocol receivedProtocol;
	private long receivedTime;   // in internal time
	private boolean receivedOk;

	private byte lastSyncByte;

	private FillableBuffer preambleBuffer;
	private FillableBuffer postambleBuffer;

	// --------------------------------------------------------------------------------
	// Constructor

	public Receiver(AbstractLink lnk, LinkPortInterface prt) {
		link = lnk;
		port = prt;
		preambleBuffer = new FillableBuffer(SEND_PREAMBLE_LENGTH);
		postambleBuffer = new FillableBuffer(SEND_POSTAMBLE_LENGTH);
		crc8 = new CRC8Calculator();
		crc16 = new CRC16Calculator();
		inputChannels = link.getInputChannels();
		resetMessageInfo();
	}

	// --------------------------------------------------------------------------------

	private void resetMessageInfo() {
		receivedSequenceNumber = 0;
		receivedChannelNumber = 0;
		receivedLength = 0;
		receivedCRC1 = 0;
		receivedCRC2 = 0;
		receivedMessage = null;
		receivedChannel = null;
		receivedProtocol = null;
		receivedOk = false;
		receivedTime = 0;
	}

	// --------------------------------------------------------------------------------
	// Do some work, the top level, gets called in a loop

	public synchronized void doWork() throws InterruptedException {
		while (true) {
			resetMessageInfo();
			receiveMessage();
			if (receivedOk) {
				receivedChannel = inputChannels.getChannelByNumber(receivedChannelNumber);
				if (receivedChannel != null) {
					receivedProtocol = receivedChannel.getProtocol();
				
					// ask the link if we are currently receiving from the channel
					// if not, discard
					if (link.isReceivableChannel(receivedChannel)) {
						handleReceivedMessage();
					}
					else {
						System.out.println(link.getRole() + " discarding received message for channel " + receivedChannelNumber);
					}
				}
				else {
					System.out.println(link.getRole() + " discarding received message for uninstalled channel " + receivedChannelNumber);
				}
			}
		}
	}

	// --------------------------------------------------------------------------------
	// Receive a message

	public void receiveMessage() throws InterruptedException {
		resetMessageInfo();

		crc8.start();
		crc16.start();

		preambleBuffer.reset();
		postambleBuffer.reset();

		try {
			receivePreSync();
			receivePreamble();
			receiveBody();
			receivePostamble();
			receivedOk = true;
		} catch (ReceiveException e) {
			handleReceiveException(e);
			receivedOk = false;
		}

		if (receivedOk) {
			receivedTime = InternalTimeSystem.currentTime();
			debugPrintMessage("Received", receivedSequenceNumber, receivedChannelNumber, receivedLength, receivedCRC1, receivedMessage, receivedCRC2);
		}
	}

	private void receivePreSync() throws InterruptedException {
		boolean synced = false;
		int sync1Count = 0;
		byte bite;

		while (!synced) {
			bite = port.readByte();
			if (bite == RECEIVE_SYNC_BYTE_1) {
				// saw another sync, just increment the count
				sync1Count++;
			} else {
				// saw something else
				if (sync1Count >= RECEIVE_SYNC_1_LENGTH) {
					// we have enough
					synced = true;
					lastSyncByte = bite;
				}
			}
		}
	}

	private void receivePreamble() throws ReceiveException,
			InterruptedException {
		preambleBuffer.addByte(lastSyncByte);
		for (int i = 0; i < RECEIVE_PREAMBLE_LENGTH - 2; i++) {
			preambleBuffer.addByte(readEscapedByte());
		}

		int expectedSequenceNumber = bestSequenceNumber();
		receivedSequenceNumber = preambleBuffer.reConstructBytes1();
		receivedChannelNumber = preambleBuffer.reConstructBytes1();
		receivedLength = preambleBuffer.reConstructBytes1();

		crc8.extend(preambleBuffer);

		preambleBuffer.addByte(readEscapedByte());
		receivedCRC1 = preambleBuffer.reConstructBytes1();
		crc8.end();

		if (receivedCRC1 != crc8.get()) {
			throw new ReceiveException("Preamble CRC mismatch");
		}
		if (receivedSequenceNumber != expectedSequenceNumber) {
			System.out.println("Expected, Received");
			System.out.println(expectedSequenceNumber);
			System.out.println(receivedSequenceNumber);
			throw new ReceiveException("Bad received sequence number");
		}
		if (!AbstractChannel.isLegalChannelNumber(receivedChannelNumber)) {
			throw new ReceiveException("Bad received channel number");
		}
		if (!LinkMessage.islegalMessageLength(receivedLength)) {
			throw new ReceiveException("Bad received message length");
		}

		crc16.extend(preambleBuffer);
	}

	private void receiveBody() throws ReceiveException, InterruptedException {
		receivedMessage = new LinkMessage(receivedChannelNumber, receivedLength);

		for (int i = 0; i < receivedLength; i++) {
			receivedMessage.addByte(readEscapedByte());
		}
		crc16.extend(receivedMessage);
	}

	private void receivePostamble() throws ReceiveException,
			InterruptedException {
		for (int i = 0; i < RECEIVE_POSTAMBLE_LENGTH; i++) {
			postambleBuffer.addByte(readEscapedByte());
		}

		receivedCRC2 = postambleBuffer.reConstructBytes2();

		crc16.end();
		if (receivedCRC2 != crc16.get()) {
			throw new ReceiveException("Preamble CRC mismatch");
		}
	}

	// --------------------------------------------------------------------------------
	// Classifying bytes - need to detect byte escapes

	private byte readEscapedByte() throws ReceiveException,
			InterruptedException {
		byte bite = port.readByte();

		switch (bite) {
		case RECEIVE_SYNC_BYTE_1:
			throw new ReceiveException("Unescaped sync byte");
		case RECEIVE_ESCAPE_BYTE:
			bite = port.readByte();
			switch (bite) {
			case RECEIVE_INDICATE_SYNC_1:
				return RECEIVE_SYNC_BYTE_1;
			case RECEIVE_INDICATE_ESCAPE:
				return RECEIVE_ESCAPE_BYTE;
			default:
				throw new ReceiveException("Unknown escaped byte");
			}
		default:
			return bite;
		}
	}

	// --------------------------------------------------------------------------------

	private static class ReceiveException extends Exception {
		private String reason;

		ReceiveException(String why) {
			reason = why;
		}
	}

	// --------------------------------------------------------------------------------

	private void handleReceivedMessage() throws InterruptedException {
		receivedProtocol.receiveMessage(receivedMessage);
	}

	private void handleReceiveException(ReceiveException e) {
		AbstractLink link = inputChannels.getLink();
		receivedTime = InternalTimeSystem.currentTime();
		link.receiveReceiverException(e);
	}


}
