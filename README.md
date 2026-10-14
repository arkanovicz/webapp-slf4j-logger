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

webapp-slf4j-logger is an [SLF4J](http://www.slf4j.org/) backend that forwards logs to a `ServletContext` object.

All log messages are logged using
[ServletContext#log](http://docs.oracle.com/javaee/6/api/javax/servlet/ServletContext.html#log%28java.lang.String,%20java.lang.Throwable%29), allowing a J2EE webapp to send its logs towards the J2EE container logs. This is pretty handy with docker or docker-compose containers, since it will show the webapp logs directly in their logs.

Features:

 * zero-config for default functional behaviour (with J2EE annotations enabled, if you're lucky that will be the default, otherwise see below)
 * Custom formats (with %date, %level, %logger, %ip, %user, %message)
 * Custom format placeholders (using slf4j MDC, aka Mapped Diagnostic Contexts), which can be used to display contextual information like source filename, line and column, etc.
 * Email notifications
 * Supports session serialization & deserialization
 * Per-class log levels
 * Errors stack traces stripping

# Building

To build the jar:

```shell
$ mvn package
```

To build the javadocs:

```shell
$ mvn javadoc:javadoc
```

# Configuration

## Inclusion in a J2EE 3.0+ webapp

If your J2EE container is complient with the 3.0 servlet API, then you just have to include webapp-slf4j-logger.jar in your `WEB-INF/lib` directory or in your `pom.xml`:

```xml
<dependency>
    <groupId>com.republicate</groupId>
    <artifactId>webapp-slf4j-logger</artifactId>
    <version>4.2</version>
    <scope>runtime</scope>
</dependency>
```

But make sure that `metadata-complete` attribute of the root `<web-app>` tag is absent or set to `false` in your webapp descriptior (the `web.xml` file).

Configuration parameters are read:

+ from context parameters, with the configuration namespace prefix:

```xml
<context-param>
  <param-name>webapp-slf4j-logger.***parameter***</param-name>
  <param-value>***value***</param-value>
</context-param>
```

+ from the `/WEB-INF/logger.properties` file, without the configuration namespace prefix:

```properties
***parameter***=***value***
```

+ from the JVM system properties, with the configuration namespace prefix

The latter takes ther precedence and overwrites the former.

Here's an example `/WEB-INF/logger.properties` file:

```properties
level = trace
level.org.apache.velocity = warn
level.org.apache.commons = error
format = %logger [%level] [%ip] %message @%file:%line:%column
notification = error:smtp:mail.foo.com:25:error@foo.com:bob@foo.com
```

## Log levels

Possible values for log levels are (case insensitive) `trace`, `debug`, `info`, `warn`,
`error`, following the standard slf4j levels.

The default log level is `info`, and can be changed in the `/WEB-INF/logger.properties` file using:

```properties
level = debug
```

or in the webapp descriptor:

```xml
<context-param>
  <param-name>webapp-slf4j-logger.level</param-name>
  <param-value>debug</param-value>
</context-param>
```

The default enabled level is INFO.

Per-class log levels are set as well using context parameters, using webapp-slf4j-logger.<logger name>.level - for instance, setting the `foo.bar` logger to `info` is done as follow:

```properties
level = debug
```

Or:

```xml
<context-param>
  <param-name>webapp-slf4j-logger.level.foo.bar</param-name>
  <param-value>info</param-value>
</context-param>
```

## Log Format

The format can be specified with a context parameter, as a sequence of placeholders and literal text.

```xml
    <context-param>
      <param-name>webapp-slf4j-logger.format</param-name>
      <param-value>%logger [%level] [%ip] %message</param-value>
    </context-param>
```

Or:

```properties
format = %logger [%level] [%ip] %message
```

Placeholders begin with '%' and must only contain alpha-numeric characters.

Predefined placeholders:

* %date - the timestamp, formatted as "YYYY-MM-DD HH:mm:ss,sss".
* %level, %Level, %LEVEL - the level in lowercase, standard case or uppercase (and left-padded to five characters).
* %logger - the name of the logger.
* %ip - the IP address of the current HTTP request - note that the logger tries hard to get the real IP address when behind proxies.
* %user - the name of the currently logged HTTP user
* %message - the actual log message string

Custom placeholders must correspond to existing MDC tags. Check the [IPTagFilter.java](https://github.com/arkanovicz/webapp-slf4j-logger/blob/master/src/main/java/com/republicate/slf4j/impl/IPTagFilter.java) class to see an example.

The default format is:

```properties
format = %logger [%level] [%ip] %message
```

(it doesn't include %date, as the date will usually be added by the J2EE container, nor does it add a terminal \n, as the container will take care of it).

## Email notifications

The logger can be configured to send an email if severity is beyond a certain level (typically, warning or error). The configuration parameter is of the form:

```properties
notification = *level*:*from_address*:*to_address*
```

The server, port, user and password are expected to be found in the environment as:

```
SMTP_HOST
SMTP_PORT
SMTP_USER
SMTP_PASSWORD
```

The `starttls` protocol is expected.

Example:

```properties
notification = warn:from@address:to@address
```

You can also set this parameter to `false` to disable the notifications. Compared to just removing the parameter, it's useful if you are generating your webapp descriptor from, let say, filtered maven resources.

## Stack trace stripping

The logger can strip out standard strack trace elements (the ones that come from Java itself, the J2EE container or the JDBC driver) so that the displayed stack traces only contain the pith and marrow code locations, the ones that correspond to your application code.

This is enabled by default in 1.6. It's configured with:

```properties
stripper = *empty*/'none'/'false' | 'default' | *coma separated list of packages prefixes* 
```

The default filtered out packages are:

- java
- javax
- com.mysql
- org.apache
- org.codehaus
- org.eclipse
- org.postgresql
- sun

For instance:

```properties
stripper = java.lang., com.foo.
```

Package names aren't supposed to end with a period, if they don't, `com.foo` will also strip out `com.foobar`.

*TODO* - allow inclusions and exclusions with `+some.package,-some.other.package`, starting from the default set.

## Inclusion in a 3.0+ webapp

The logger uses an annotated ServletContextListener which should be picked up by the servlet container at initialization, provided that you did not set the `metadata-complete=true` attribute in the root `<webapp>` tag, so all you have to do is have the `webapp-slf4j-logger` present in `WEB-INF/lib`.

Note that if the order in which servlet context listeners are initialized is important to you (like if you want to use the logger from within another servlet context listener), then you *should* explicitely declare all listeners, including the `com.republicate.slf4j.impl.ServletContextLoggerListener`, in your webapp descriptor as detailed below.

## Inclusion in a non-3.0 webapp, or in a 3.0+ webapp with `metadata-complete=true`

If your J2EE container is not complient with servlet API 3.0, you have to add to `web.xml`:

```xml
<listener>
  <listener-class>com.republicate.slf4j.impl.ServletContextLoggerListener</listener-class>
</listener>
```

And if you want to enable the %ip format tag, you'll also have to add the following filter:

```xml
<filter>
  <filter-name>webapp-slf4j-logger-ip-tag-filter</filter-name>
  <filter-class>com.republicate.slf4j.impl.IPTagFilter</filter-class>
</filter>
```

with its mapping:

```xml
<filter-mapping>
  <filter-name>webapp-slf4j-logger-ip-tag-filter</filter-name>
  <url-pattern>/*</url-pattern>
  <dispatcher>REQUEST</dispatcher>
  <dispatcher>FORWARD</dispatcher>
</filter-mapping>
```

## FAQ

### All other SLF4J jars begin with "slf4j". Why isn't this library called slf4j-webapp-logger?

Some containers, Tomcat at least, will not search for servlet-3.0 annotations in a certain number of jars, among which all slf4j-* jars...
