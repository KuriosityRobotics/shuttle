# Shuttle

Shuttle is a lightweight concurrency library aimed to help control robots that introduces synchronous and structured control flow into robot mechanism control, as explained in [this blog post](https://max.xz.ax/blog/structured-concurrency-robot-control/).

```java
public void releaseAndCollapse() throws InterruptedException {
    try (var scope = HardwareTaskScope.open()) {
        scope.fork(arm::collapse);
        scope.fork(() -> slides.goToPosition(RETRACTED));

        scope.join();
    }
}
```

Shuttle is comprised of two main parts, split into separate gradle modules:

`shuttle-core` contains:
- An FTC-compatible backport of the Java 21 structured concurrency API.  You will interact with this through the HardwareTaskScope class.
- An FTC-compatible backport of the Java 8 java.time API.  You will interact with this through the Instant and Duration classes.

`shuttle-hardware` contains:
- Blocking abstractions for FTC motors.  You will interact with these by extending the ServoControl and LinearMotorControl classes.

Examples for how Shuttle can be used are located in our [Center Stage](https://github.com/KuriosityRobotics/center-stage) repository.
# Installation
Shuttle can be added to the FTC SDK by adding the Kuriosity maven repository to `TeamCode/build.gradle`, and adding the following dependencies in the `implementation` section:
```gradle
repositories {
    maven {
        url = "https://maven.kuriosityrobotics.com/releases/"
    }
}

dependencies {
    // other stuff

    implementation "com.kuriosityrobotics.shuttle:shuttle-core:1.1"
    implementation "com.kuriosityrobotics.shuttle:shuttle-hardware:1.0"
}
```

Optionally, the project can be updated to use newer features such as `var` declarations by changing the Android `compileOptions` to use Java 11 `build.common.gradle` in the project's root directory:
```gradle
compileOptions {
    sourceCompatibility JavaVersion.VERSION_11
    targetCompatibility JavaVersion.VERSION_11
}
```

# Documentation
coming soon


