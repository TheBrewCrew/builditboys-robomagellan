package com.builditboys.robots.time;

import java.util.Date;

import com.builditboys.robots.units.TimeUnit;


public class InternalTimeSystem extends AbstractTimeSystem {
	
	private static final InternalTimeSystem instance = new InternalTimeSystem("local");
	
	//--------------------------------------------------------------------------------

	private InternalTimeSystem (String nm) {
		super(nm);
	}
	
	//--------------------------------------------------------------------------------
		
	public static long currentInternalTime () {
		return instance.currentTimeLong();
	}

	
	public static Date toDate (long time) {
		return new Date(TimeUnit.convert(time, units, TimeUnit.MILLISECONDS));
	}
	

}