# android-ephemeral

A super lightweight library to store and retrieve values that expire after a duration you choose. Values can live in memory (any type) or in shared preferences (primitives). No dependencies beyond the Kotlin standard library.

## Dependency

In your `build.gradle` add the following to `dependencies`:

```
implementation 'io.github.ashwinbhaskar:ephemeral-android:2.0.0'
```

Requires `minSdk 26` (for `java.time.Duration`).

## Usage

### In memory

```kotlin
import java.time.Duration
import com.ephemeral.InMemory

data class SomeClass(val str: String, val i: Int, val b: Boolean)
val sc = SomeClass("quixx", 1, true)

// Put a value in memory which expires after 5 seconds
InMemory.put(key = "foo-key", value = sc, expireAfter = Duration.ofSeconds(5))

// Get it back. null means the value has expired or was never there.
val value: SomeClass? = InMemory.get<SomeClass>("foo-key")

// Return the cached value, or compute, store and return it if there is none
val user: User = InMemory.getOrPut("user", Duration.ofMinutes(10)) { api.fetchUser() }

// Update an existing value in place, keeping its expiry. Returns false if it is absent or expired.
val didUpdate: Boolean = InMemory.updateValueIfPresent<SomeClass>("foo-key") { it.copy(str = "quixx100") }

// Read a value and push its expiry out to 5 seconds from now
val refreshed: SomeClass? = InMemory.getAndUpdateExpiryIfPresent<SomeClass>("foo-key", Duration.ofSeconds(5))

// Remove a key. Returns true if a live value was removed.
val isRemoved: Boolean = InMemory.remove("foo-key")

// Expired entries are swept out automatically on put (at most once a second).
// Call this to reclaim memory sooner.
InMemory.purgeExpired()
```

Asking for a key as a type other than the one stored under it throws a `ClassCastException` naming the key and both types. That is a programming error, so it is not folded into the `null` case.

Every method also has an overload taking a `KClass` for callers that cannot use reified generics, for example `InMemory.get("foo-key", SomeClass::class)`.

`InMemory` is safe to use from any thread.

### In shared preferences

A wrapper around the shared preference getters and setters with an extra field, `expireAfter`. Supports `String`, `Boolean`, `Int`, `Long`, `Float` and `Set<String>`.

```kotlin
import java.time.Duration
import com.ephemeral.Preferences
import com.ephemeral.Extensions.*

// Put a value in shared preferences which expires after 5 seconds
Preferences.putString("some-key", "this is the value", Duration.ofSeconds(5), applicationContext)

// Or use the extension functions on Context
context.putEphemeralString("some-key", "this is the value", Duration.ofSeconds(5))

val defaultValue = ""
val value = context.getEphemeralString("some-key", defaultValue)
when (value) {
    defaultValue -> // The value has either expired or was never there
    else -> // do something with the value
}

// Remove a key before it expires
Preferences.removeKey("some-key", context)

// Expired entries are swept out automatically on put (at most once a second).
// Call this to reclaim space sooner.
Preferences.purgeExpired(context)
```

Values are kept in a private preferences file of their own, and expiries in a second one, so the library never collides with your own preference keys.

## Releasing

A release is published to Maven Central by GitHub Actions when a version tag is pushed. The workflow lives in `.github/workflows/publish.yml`.

1. Set `PUBLISH_VERSION` in `app/ephemeral/build.gradle` to the new version and merge to `master`.
2. Tag that commit and push the tag:
   ```
   git tag v2.0.0
   git push origin v2.0.0
   ```
3. Watch the "Publish to Maven Central" workflow. It refuses to publish if the tag is not on `master` or does not match `PUBLISH_VERSION`, runs the unit tests, then signs, uploads and releases the artifact. It appears on Maven Central within about half an hour.

The workflow reads these repository secrets: `OSSRH_USERNAME` and `OSSRH_PASSWORD` (a Central Portal user token, not OSSRH credentials), `SIGNING_KEY_ID`, `SIGNING_PASSWORD` and `SIGNING_KEY` (the ASCII-armoured GPG secret key, from `gpg --armor --export-secret-keys <key id>`).

To publish from a machine instead, put the same values in `local.properties` as `ossrhUsername`, `ossrhPassword`, `signing.keyId`, `signing.password` and `signing.secretKeyRingFile`, then run `gradle publishToSonatype closeAndReleaseSonatypeStagingRepository`.

## Migrating from 1.x

Version 2 removes the Arrow dependency and changes the `InMemory` API:

- `get` returns `T?` instead of `Either<CastError, Option<T>>`. `null` means absent or expired.
- A type mismatch throws `ClassCastException` instead of returning a `CastError`. `unsafe()` is gone.
- Methods take a reified type parameter: `InMemory.get<SomeClass>("key")`. The `KClass` overloads remain, with the class as the last parameter before any lambda.
- `getAndUpdateExpiryIfPresent` and `updateValueIfPresent` no longer touch a value that has already expired.

`Preferences` keeps the same API. Expiries are now stored as epoch milliseconds in a separate file; values written by 1.x are read and migrated transparently. The misspelled `Context.putEphemeraFloat` is deprecated in favour of `putEphemeralFloat`.
