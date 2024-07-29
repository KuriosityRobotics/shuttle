package com.kuriosityrobotics.shuttle;

import java.util.Objects;

class Util {
	public static String toIdentityString(Object o) {
		Objects.requireNonNull(o);
		return o.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(o));
	}
}
