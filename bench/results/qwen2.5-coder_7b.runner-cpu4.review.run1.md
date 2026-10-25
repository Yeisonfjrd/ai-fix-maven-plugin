# ai-fix benchmark · qwen2.5-coder:7b

Options: `aifix.numGpu=0 aifix.numThread=4`

Run on 2026-10-25. **9/10 fixed** (visible tests green), **7/10 real fixes** (hidden tests green too). Average per case: 34 s in the model, 61 s in total.

| Case | Bug | Result | Attempts | Review | Model | Hidden tests | Total |
|---|---|---|---|---|---|---|---|
| 01-paginator | Off-by-one: each page drops its last item. | real fix | 2 | ok | 71 s | 4/4 | 104 s |
| 02-money-split | Integer division: splitting money loses the remainder cents. | real fix | 1 | ok | 30 s | 4/4 | 60 s |
| 03-null-nickname | NullPointerException: a user without a nickname breaks the display name. | real fix | 1 | ok | 20 s | 3/3 | 46 s |
| 04-missing-hashcode | equals without hashCode: equal coordinates are not equal inside a HashSet. | real fix | 2 | ok | 59 s | 2/2 | 88 s |
| 05-slug | String handling: repeated or surrounding spaces produce double dashes. | **incomplete** | 1 | ok | 16 s | 2/3 | 41 s |
| 06-age | Dates: age counts a year before the birthday has happened. | **incomplete** | 1 | ok | 18 s | 2/3 | 43 s |
| 07-shared-counter | Shared state: a static field makes every counter share one count. | real fix | 1 | ok | 15 s | 2/2 | 39 s |
| 08-remove-while-iterating | Collections: removing by index while looping skips the next element. | real fix | 1 | ok | 22 s | 3/3 | 47 s |
| 09-version-compare | Comparison: versions compared as text put 1.10 before 1.9. | real fix | 1 | ok | 37 s | 4/4 | 61 s |
| 10-tax-category-case | Bug in another class: the tax table is case-sensitive, the test only uses Invoice. | not fixed | 3 | - | 52 s | - | 78 s |

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
@@ -1,5 +1,7 @@
 package bench;
 
+import java.util.Objects;
+
 public final class Coordinate {
 
     private final int x;
@@ -20,6 +22,14 @@
 
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
+        return Objects.hash(x, y);
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
+        return title.toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
     }
 }
```

### 06-age

```diff
--- a/src/main/java/bench/Age.java
+++ b/src/main/java/bench/Age.java
@@ -8,6 +8,10 @@
 
     /** Full years lived on `today`. */
     public static int years(LocalDate birth, LocalDate today) {
-        return today.getYear() - birth.getYear();
+        int years = today.getYear() - birth.getYear();
+        if (today.isBefore(birth.withYear(today.getYear()))) {
+            years--;
+        }
+        return years;
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
@@ -9,6 +9,17 @@
      * Negative if a is older, positive if a is newer, 0 if equal.
      */
     public static int compare(String a, String b) {
-        return a.compareTo(b);
+        String[] aParts = a.split("\\.");
+        String[] bParts = b.split("\\.");
+
+        int length = Math.max(aParts.length, bParts.length);
+        for (int i = 0; i < length; i++) {
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

