package io.github.yeisonfjrd.aifix;

/** Un test que falló, tal como lo deja Surefire en target/surefire-reports. */
public record TestFailure(String className, String methodName, String message, String stackTrace) {

    /** El formato que entiende -Dtest: Clase#metodo. */
    public String selector() {
        return className + "#" + methodName;
    }

    public String simpleClassName() {
        return className.substring(className.lastIndexOf('.') + 1);
    }
}
