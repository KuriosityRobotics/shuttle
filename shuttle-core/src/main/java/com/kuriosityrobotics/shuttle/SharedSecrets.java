package com.kuriosityrobotics.shuttle;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

class SharedSecrets {
	private static final JavaLangAccess JLA = new JavaLangAccess() {
		private final Map<Thread, ThreadContainer> CONTAINERS = Collections.synchronizedMap(new WeakHashMap<>());
		private final ThreadLocal<StackableScope> HEAD_STACKABLE_SCOPE = new ThreadLocal<>();

		public ThreadContainer threadContainer(Thread thread) {
			return CONTAINERS.get(thread);
		}

		public void start(Thread thread, ThreadContainer container) {
			CONTAINERS.put(thread, container);
			thread.start();
		}

		public StackableScope headStackableScope(Thread thread) {
			return HEAD_STACKABLE_SCOPE.get();
		}

		public void setHeadStackableScope(StackableScope scope) {
			HEAD_STACKABLE_SCOPE.set(scope);
		}

	};

	public static JavaLangAccess getJavaLangAccess() {
		return JLA;
	}
}
