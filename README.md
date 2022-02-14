# android-ephemeral

A super lightweight library to help you store/retrieve ephemeral values which can be either persisted (in shared preferences) or put in memory.

## Dependency
In your build.gradle file add the following in dependencies
```
implementation 'io.github.ashwinbhaskar:ephemeral-android:1.0.0'
```

## Usage

### In Memory

```kotlin
import java.time.*
import arrow.core.*
import com.ephemeral.*

data class SomeClass(str: String, i: Int, b: Boolean)
val sc = SomeClass("quixx", 1, true)

//Put a value in memory which gets deleted after 5 seconds
ImMemory.put(key = "foo-key", value = sc, expireAfter = Duration.ofSeconds(5))

// Gets the value from memory
val maybeValueOrError: Either<CastError, Option<SomeClass>> = ImMemory.get("foo-key", SomeClass::class)

//Use unsafe() method if you are confident that SomeClass is the right type
val maybeValue: Option<SomeClass> = InMemory.get("foo-key", SomeClass::class).unsafe()

when(maybeValue) {
 is None -> // The value has either expired or was never there
 is Some -> // do something with maybeValue.value
}

//Update an existing value by providing an update function
val didUpdate: Boolean = InMemory.updateValueIfPresent(key = "foo-key", updateFun = {sc -> sc.copy(str = "quixx100")}, SomeClass::class)

//Update the expiry time as you access the value
val maybeValue: Option<SomeClass> = InMemory.getAndUpdateExpiryIfPresent("foo-key", Duration.ofSeconds(5), SomeClass::class).unsafe()

//Remove a key-value
val isRemoved: Boolean = InMemory.remove("foo-key")

```

### In Shared Preferences

This is a wrapper around android shared preference methods with an extra field - `expireAfter`.

```kotlin
import java.time.*
import com.ephemeral.*
import com.ephemeral.Extensions.*

//Put a value in shared preferences which gets deleted after 5 seconds
Persisted.putString("some-key", "this is a the value", Duration.ofSeconds(5), applicationContext)

//If you do not want to pass applicationContext everytime can you use the extension functions on Context that are imported above
context.putEphemeralString("some-key", "this is a the value", Duration.ofSeconds(5))

val defaultValue = ""

val value = context.getEphemeralString("some-key", defaultValue)
when(value) {
    defaultValue -> //The value has either expired or was never there
    else -> // do something with the value
}

//Similarly you can use methods for Boolean, Int, Long, Float, Set<String>
```


