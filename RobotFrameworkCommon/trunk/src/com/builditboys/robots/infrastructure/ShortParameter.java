package com.builditboys.robots.infrastructure;

public class ShortParameter extends AbstractParameter implements ParameterInterface {
	
	Short value = 0;
	
	//--------------------------------------------------------------------------------
	
	public ShortParameter (String nm) {
		name = nm;
	}
	
	public ShortParameter (String nm, Short val) {
		name = nm;
		value = val;
	}

	//--------------------------------------------------------------------------------

	public synchronized Short getValue() {
		return value;
	}

	public synchronized void setValue(Short value) {
		this.value = value;
	}
	
	// --------------------------------------------------------------------------------

	public String toString() {
		return "Short parm: " + value;
	}


}
