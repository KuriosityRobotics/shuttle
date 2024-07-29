package com.kuriosityrobotics.shuttle;

interface JavaLangAccess {

	/**
	 * Returns the ThreadContainer for a thread, may be null.
	 */
	ThreadContainer threadContainer(Thread thread);

	/**
	 * Starts a thread in the given ThreadContainer.
	 */
	void start(Thread thread, ThreadContainer container);

	/**
	 * Returns the top of the given thread's stackable scope stack.
	 */
	StackableScope headStackableScope(Thread thread);

	/**
	 * Sets the top of the current thread's stackable scope stack.
	 */
	void setHeadStackableScope(StackableScope scope);

}
