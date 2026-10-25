# ai-fix benchmark · qwen2.5-coder:7b

Options: `aifix.numGpu=0 aifix.numThread=4`

Run on 2026-10-25. **9/10 fixed** (visible tests green), **7/10 real fixes** (hidden tests green too). Average per case: 25 s in the model, 51 s in total.

| Case | Bug | Result | Attempts | Review | Model | Hidden tests | Total |
|---|---|---|---|---|---|---|---|
| 01-paginator | Off-by-one: each page drops its last item. | real fix | 1 | ok | 25 s | 4/4 | 50 s |
| 02-money-split | Integer division: splitting money loses the remainder cents. | real fix | 1 | ok | 24 s | 4/4 | 49 s |
| 03-null-nickname | NullPointerException: a user without a nickname breaks the display name. | real fix | 1 | ok | 20 s | 3/3 | 45 s |
| 04-missing-hashcode | equals without hashCode: equal coordinates are not equal inside a HashSet. | real fix | 2 | ok | 53 s | 2/2 | 82 s |
| 05-slug | String handling: repeated or surrounding spaces produce double dashes. | **incomplete** | 1 | ok | 12 s | 2/3 | 36 s |
| 06-age | Dates: age counts a year before the birthday has happened. | real fix | 1 | ok | 18 s | 3/3 | 43 s |
| 07-shared-counter | Shared state: a static field makes every counter share one count. | real fix | 1 | ok | 11 s | 2/2 | 35 s |
| 08-remove-while-iterating | Collections: removing by index while looping skips the next element. | not fixed | 3 | - | 45 s | - | 71 s |
| 09-version-compare | Comparison: versions compared as text put 1.10 before 1.9. | real fix | 1 | ok | 31 s | 4/4 | 56 s |
| 10-tax-category-case | Bug in another class: the tax table is case-sensitive, the test only uses Invoice. | **incomplete** | 1 | ok | 15 s | 2/3 | 39 s |

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
+        int to = Math.min(from + size, items.size()); // Fix: change to + size instead of + size - 1
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
@@ -6,9 +6,13 @@
 
     /** The nickname if the user has one, otherwise first and last name. */
     public static String displayName(User user) {
-        if (user.nickname().isBlank()) {
+        String nickname = user.nickname();
+        if (nickname == null) {
+            nickname = "";
+        }
+        if (nickname.isBlank()) {
             return user.first() + " " + user.last();
         }
-        return user.nickname();
+        return nickname;
     }
 }
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
@@ -22,4 +24,9 @@
     public boolean equals(Object o) {
         return o instanceof Coordinate c && c.x == x && c.y == y;
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
@@ -8,6 +8,11 @@
 
     /** Full years lived on `today`. */
     public static int years(LocalDate birth, LocalDate today) {
-        return today.getYear() - birth.getYear();
+        int years = today.getYear() - birth.getYear();
+        if (today.getMonthValue() < birth.getMonthValue() || 
+            (today.getMonthValue() == birth.getMonthValue() && today.getDayOfMonth() < birth.getDayOfMonth())) {
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

### 10-tax-category-case

```diff
--- a/src/main/java/bench/Invoice.java
+++ b/src/main/java/bench/Invoice.java
@@ -18,7 +18,7 @@
     public long tax() {
         long total = 0;
         for (Line line : lines) {
-            total += line.cents() * taxes.perMille(line.category()) / 1000;
+            total += line.cents() * taxes.perMille(line.category().toUpperCase()) / 1000;
         }
         return total;
     }
```

