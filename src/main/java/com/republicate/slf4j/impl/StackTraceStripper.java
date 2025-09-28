package com.republicate.slf4j.impl;


import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * <p>Utility to strip out unwanted packages from throwables stack traces, so that you only get the pith and marrow lines of logs in you error logs.</p>
 * <p>Default filtered packages:</p>
 * <ul>
 *   <li>java</li>
 *   <li>javax</li>
 *   <li>jakarta</li>
 *   <li>com.mysql</li>
 *   <li>org.apache</li>
 *   <li>org.codehaus</li>
 *   <li>org.eclipse</li>
 *   <li>org.postgresql</li>
 *   <li>sun</li>
 * </ul>
 */

public class StackTraceStripper implements Serializable
{
    private NavigableSet<String> unwantedPackages = new TreeSet<>();

    private static NavigableSet<String> defaultUnwantedPackages = new TreeSet<>();
    static
    {
        // packages don't have to end with '.', but it avoids potential false positives
        defaultUnwantedPackages.add("java.");
        defaultUnwantedPackages.add("javax.");
        defaultUnwantedPackages.add("jakarta.");
        defaultUnwantedPackages.add("com.mysql.");
        defaultUnwantedPackages.add("org.apache.");
        defaultUnwantedPackages.add("org.codehaus.");
        defaultUnwantedPackages.add("org.eclipse.");
        defaultUnwantedPackages.add("org.postgresql.");
        defaultUnwantedPackages.add("sun.");
        defaultUnwantedPackages.add("jdk.");
    }

    public StackTraceStripper()
    {
        this(defaultUnwantedPackages);
    }

    public StackTraceStripper(Collection<String> unwantedPackages)
    {
        this.unwantedPackages.addAll(unwantedPackages);
    }

    public StackTraceStripper(String unwantedPackages)
    {
        this.unwantedPackages.addAll(Arrays.asList(unwantedPackages.split("\\s*,\\s*")));
    }

    public void strip(Throwable throwable)
    {
        // avoid self-reference
        Set<Throwable> seen = new HashSet<>();

        while (throwable != null)
        {
            stripStackTrace(throwable);
            seen.add(throwable);
            throwable = throwable.getCause();
            if (seen.contains(throwable))
            {
                throwable = null;
            }
        }
    }

    private void stripStackTrace(Throwable throwable)
    {
        StackTraceElement stackTrace[] = throwable.getStackTrace();
        if (stackTrace == null)
        {
            return;
        }
        stackTrace = Arrays.stream(stackTrace)
            .filter(element ->
            {
                String className = element.getClassName();
                String lowerBound = element == null ? null : unwantedPackages.lower(className);
                return lowerBound == null || !className.startsWith(lowerBound);
            })
            .collect(Collectors.toList())
            .toArray(new StackTraceElement[0]);
        throwable.setStackTrace(stackTrace);
    }
}
