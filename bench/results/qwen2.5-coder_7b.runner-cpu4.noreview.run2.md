# ai-fix benchmark · qwen2.5-coder:7b

Options: `aifix.numGpu=0 aifix.numThread=4 aifix.review=false`

Run on 2026-10-25. **8/10 fixed** (visible tests green), **7/10 real fixes** (hidden tests green too). Average per case: 27 s in the model, 52 s in total.

| Case | Bug | Result | Attempts | Review | Model | Hidden tests | Total |
|---|---|---|---|---|---|---|---|
| 01-paginator | Off-by-one: each page drops its last item. | real fix | 2 | off | 45 s | 4/4 | 76 s |
| 02-money-split | Integer division: splitting money loses the remainder cents. | real fix | 1 | off | 20 s | 4/4 | 46 s |
| 03-null-nickname | NullPointerException: a user without a nickname breaks the display name. | real fix | 1 | off | 11 s | 3/3 | 36 s |
| 04-missing-hashcode | equals without hashCode: equal coordinates are not equal inside a HashSet. | real fix | 2 | off | 57 s | 2/2 | 86 s |
| 05-slug | String handling: repeated or surrounding spaces produce double dashes. | **incomplete** | 1 | off | 9 s | 1/3 | 33 s |
| 06-age | Dates: age counts a year before the birthday has happened. | not fixed | 3 | - | 36 s | - | 61 s |
| 07-shared-counter | Shared state: a static field makes every counter share one count. | real fix | 1 | off | 8 s | 2/2 | 32 s |
| 08-remove-while-iterating | Collections: removing by index while looping skips the next element. | real fix | 1 | off | 14 s | 3/3 | 39 s |
| 09-version-compare | Comparison: versions compared as text put 1.10 before 1.9. | real fix | 1 | off | 23 s | 4/4 | 47 s |
| 10-tax-category-case | Bug in another class: the tax table is case-sensitive, the test only uses Invoice. | not fixed | 3 | - | 44 s | - | 69 s |

## Diffs

### 01-paginator

```diff
--- a/src/main/java/bench/Paginator.java
+++ b/src/main/java/bench/Paginator.java
@@ -11,7 +11,7 @@
         if (page < 1 || size < 1) throw new IllegalArgumentException("page and size start at 1");
         int from = (page - 1) * size;
         if (from >= items.size()) return List.of();
-        int to = Math.min(from + size - 1, items.size());
+        int to = Math.min(from + size, items.size());
         return items.subList(from, to);
     }
 }
```

### 02-money-split

```diff
--- a/src/main/java/bench/Money.java
+++ b/src/main/java/bench/Money.java
@@ -9,8 +9,9 @@
         if (parts < 1) throw new IllegalArgumentException("parts must be positive");
         long[] out = new long[parts];
         long each = cents / parts;
+        long remainder = cents % parts;
         for (int i = 0; i < parts; i++) {
-            out[i] = each;
+            out[i] = each + (i < remainder ? 1 : 0);
         }
         return out;
     }
```

### 03-null-nickname

```diff
--- a/src/main/java/bench/Names.java
+++ b/src/main/java/bench/Names.java
@@ -6,7 +6,7 @@
 
     /** The nickname if the user has one, otherwise first and last name. */
     public static String displayName(User user) {
-        if (user.nickname().isBlank()) {
+        if (user.nickname() == null || user.nickname().isBlank()) {
             return user.first() + " " + user.last();
         }
         return user.nickname();
```

### 04-missing-hashcode

```diff
--- a/src/main/java/bench/Coordinate.java
+++ b/src/main/java/bench/Coordinate.java
@@ -20,6 +20,14 @@
 
     @Override
     public boolean equals(Object o) {
-        return o instanceof Coordinate c && c.x == x && c.y == y;
+        if (this == o) return true;
+        if (o == null || getClass() != o.getClass()) return false;
+        Coordinate coordinate = (Coordinate) o;
+        return x == coordinate.x && y == coordinate.y;
     }
+
+    @Override
+    public int hashCode() {
+        return 31 * x + y;
+    }
 }
```

### 05-slug

```diff
--- a/src/main/java/bench/Slug.java
+++ b/src/main/java/bench/Slug.java
@@ -8,6 +8,6 @@
 
     /** Lowercase words joined by a single dash, with no dash at either end. */
     public static String of(String title) {
-        return title.toLowerCase(Locale.ROOT).replace(" ", "-");
+        return title.toLowerCase(Locale.ROOT).replaceAll(" +", "-");
     }
 }
```

### 07-shared-counter

```diff
--- a/src/main/java/bench/Counter.java
+++ b/src/main/java/bench/Counter.java
@@ -2,7 +2,7 @@
 
 public final class Counter {
 
-    private static int count;
+    private int count;
 
     public void increment() {
         count++;
```

### 08-remove-while-iterating

```diff
--- a/src/main/java/bench/Inventory.java
+++ b/src/main/java/bench/Inventory.java
@@ -9,10 +9,6 @@
 
     /** Removes the products that expired before `today`. A product that expires today stays. */
     public static void removeExpired(List<Product> products, LocalDate today) {
-        for (int i = 0; i < products.size(); i++) {
-            if (products.get(i).expires().isBefore(today)) {
-                products.remove(i);
-            }
-        }
+        products.removeIf(product -> product.expires().isBefore(today));
     }
 }
```

### 09-version-compare

```diff
--- a/src/main/java/bench/Versions.java
+++ b/src/main/java/bench/Versions.java
@@ -9,6 +9,16 @@
      * Negative if a is older, positive if a is newer, 0 if equal.
      */
     public static int compare(String a, String b) {
-        return a.compareTo(b);
+        String[] aParts = a.split("\\.");
+        String[] bParts = b.split("\\.");
+        int maxLength = Math.max(aParts.length, bParts.length);
+        for (int i = 0; i < maxLength; i++) {
+            int aPart = i < aParts.length ? Integer.parseInt(aParts[i]) : 0;
+            int bPart = i < bParts.length ? Integer.parseInt(bParts[i]) : 0;
+            if (aPart != bPart) {
+                return Integer.compare(aPart, bPart);
+            }
+        }
+        return 0;
     }
 }
```

