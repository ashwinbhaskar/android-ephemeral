# android-ephemeral

This is a small library to help you store ephemeral values which can be either persistable or in memory. To persist the values the library uses shared preferences.

## Dependency
In your build.gradle file add the following in dependencies
```
implementation 'io.github.ashwinbhaskar:ephemeral-android:0.1.0'
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

//Use unsafe() method if you are confident that SomeClass is the right one
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
