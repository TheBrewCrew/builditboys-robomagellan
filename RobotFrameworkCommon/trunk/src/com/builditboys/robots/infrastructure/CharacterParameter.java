package com.builditboys.robots.infrastructure;

public class CharacterParameter extends AbstractParameter implements ParameterInterface {
	
	Character value = 0;
	
	//--------------------------------------------------------------------------------

	public CharacterParameter (String nm) {
		name = nm;
	}
	
	public CharacterParameter (String nm, Character val) {
		name = nm;
		value = val;
	}

	//--------------------------------------------------------------------------------

	public synchronized Character getValue() {
		return value;
	}

	public synchronized void setValue(Character value) {
		this.value = value;
	}
	// --------------------------------------------------------------------------------

	public String toString() {
		return "Character parm: " + value;
	}


}
