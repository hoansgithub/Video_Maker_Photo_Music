# Database Query Safety Rules

## Core Rule: NEVER Fetch All Records

Assume every table can have **billions of rows**. Every query MUST be bounded.

Applies to: **Supabase, SQL, Room, SwiftData, CoreData, Firebase, MongoDB, Realm, etc.**

## Rules

### 1. Every Query MUST Have a LIMIT / Pagination
```swift
// ❌ FORBIDDEN
let allUsers = try modelContext.fetch(FetchDescriptor<User>())

// ✅ REQUIRED
var descriptor = FetchDescriptor<User>()
descriptor.fetchLimit = 20
descriptor.fetchOffset = page * 20
let users = try modelContext.fetch(descriptor)
```

```kotlin
// ❌ FORBIDDEN
@Query("SELECT * FROM users")
suspend fun getAllUsers(): List<User>

// ✅ REQUIRED
@Query("SELECT * FROM users LIMIT :limit OFFSET :offset")
suspend fun getUsers(limit: Int, offset: Int): List<User>
```

### 2. ALL Filtering MUST Be in the Query
```swift
// ❌ FORBIDDEN - Client-side filtering
let allUsers = try modelContext.fetch(FetchDescriptor<User>())
let active = allUsers.filter { $0.isActive }

// ✅ REQUIRED - Query-level filtering
var descriptor = FetchDescriptor<User>(
    predicate: #Predicate { $0.isActive }
)
descriptor.fetchLimit = 20
```

```kotlin
// ❌ FORBIDDEN - Client-side filtering
val active = repository.getAllUsers().filter { it.isActive }

// ✅ REQUIRED - Query-level filtering
@Query("SELECT * FROM users WHERE is_active = 1 LIMIT :limit OFFSET :offset")
suspend fun getActiveUsers(limit: Int, offset: Int): List<User>
```

### 3. ALL Sorting MUST Be in the Query
```swift
// ❌ FORBIDDEN - Client-side sorting
let users = try modelContext.fetch(FetchDescriptor<User>())
let sorted = users.sorted { $0.name < $1.name }

// ✅ REQUIRED - Query-level sorting
var descriptor = FetchDescriptor<User>(
    sortBy: [SortDescriptor(\.name)]
)
descriptor.fetchLimit = 20
```

```kotlin
// ❌ FORBIDDEN - Client-side sorting
val sorted = repository.getUsers().sortedBy { it.name }

// ✅ REQUIRED - Query-level sorting
@Query("SELECT * FROM users ORDER BY name ASC LIMIT :limit OFFSET :offset")
suspend fun getUsersSorted(limit: Int, offset: Int): List<User>
```

### 4. Pagination MUST Be Query-Level
```swift
// ❌ FORBIDDEN - Client-side pagination
let all = try modelContext.fetch(FetchDescriptor<User>())
let page = Array(all[offset..<min(offset + limit, all.count)])

// ✅ REQUIRED - Query-level pagination
var descriptor = FetchDescriptor<User>()
descriptor.fetchLimit = pageSize
descriptor.fetchOffset = page * pageSize
```

```kotlin
// ❌ FORBIDDEN - Client-side pagination
val page = repository.getAllUsers().drop(offset).take(limit)

// ✅ REQUIRED - Query-level pagination (cursor or offset)
@Query("SELECT * FROM users WHERE id > :lastId ORDER BY id LIMIT :limit")
suspend fun getUsersAfter(lastId: String, limit: Int): List<User>
```

### 5. Supabase / REST API Queries
```swift
// ❌ FORBIDDEN
let response = try await supabase.from("users").select()

// ✅ REQUIRED
let response = try await supabase.from("users")
    .select()
    .eq("is_active", value: true)
    .order("name")
    .range(from: offset, to: offset + limit - 1)
```

```kotlin
// ❌ FORBIDDEN
val response = supabase.from("users").select()

// ✅ REQUIRED
val response = supabase.from("users")
    .select()
    .eq("is_active", true)
    .order("name", Order.ASCENDING)
    .range(from = offset.toLong(), to = (offset + limit - 1).toLong())
```

## Detection Commands

```bash
# Find unbounded fetches - iOS
grep -rn "FetchDescriptor<" --include="*.swift" | grep -v "fetchLimit"
grep -rn "\.fetch(" --include="*.swift" | grep -v "fetchLimit\|predicate"
grep -rn "supabase.*\.select()" --include="*.swift" | grep -v "range\|limit"

# Find unbounded fetches - Android
grep -rn 'SELECT \*.*FROM' --include="*.kt" | grep -vi "limit\|where"
grep -rn "\.select()" --include="*.kt" | grep -v "range\|limit"

# Find client-side filtering/sorting
grep -rn "\.filter\s*{" --include="*.swift" --include="*.kt"
grep -rn "\.sorted\s*{" --include="*.swift" --include="*.kt"
grep -rn "\.sortedBy\s*{" --include="*.kt"
```
