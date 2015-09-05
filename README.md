<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

webapp-slf4j-logger
===================

This is a simple logger with minimal configuration.  It is an
[SLF4J](http://www.slf4j.org/) backend that forwards logs to a
`ServletContext` object.

All log messages are logged using
[ServletContext#log](http://docs.oracle.com/javaee/6/api/javax/servlet/ServletContext.html#log%28java.lang.String,%20java.lang.Throwable%29).

Features:

 * Serialization and desierialization
 * Custom formats
 * Mapped Diagnostic Contexts (MDC)

# Configuration

## Inclusion in a maven-based project

Declare a dependency on `webapp-slf4j-logger`:

    <dependency>
      <groupId>com.republicate</groupId>
      <artifactId>webapp-slf4j-logger</artifactId>
      <version>1.0.0</version>
    </dependency>

## Inclusion in a non-maven based project

Just include webapp-slf4j-logger in WEB-INF/lib.

If your J2EE container is not complient with servlet API 3.0, you have to add to `web.xml`:

    <listener>
      <listener-class>com.republicate.slf4j.impl.ServletContextLoggerSCL</listener-class>
    </listener>

## Log level

The logging level can be set with a context parameter.  Possible
values are (case insensitive) `trace`, `debug`, `info`, `warn`,
`error`, following the standard slf4j levels.

    <context-param>
      <param-name>ServletContextLogger.level</param-name>
      <param-value>debug</param-value>
    </context-param>

## Format

The format can be specified with a context parameter, as a sequence of placeholders and literal text.

    <context-param>
      <param-name>ServletContextLogger.format</param-name>
      <param-value>%date [%ip] [%level] %logger %message</param-value>
    </context-param>

Placeholders begin with '%' and must only contain alpha-numeric characters.

Predefined placeholders:

* %date - the timestamp, formatted as "YYYY-MM-DD HH:mm:ss,sss".
* %level, %Level, %LEVEL - the level in lowercase, standard case or uppercase (and left-padded to five characters).
* %logger - the name of the logger (for class names, the package is not shown).
* %message - the actual log message string

Custom placeholders must correspond to existing MDC tags. For instance, to see IPs of each log line's request,
you can use the provided com.republicate.slf4j.helpers.IPFilter, and set up a format with the %ip placeholder.

